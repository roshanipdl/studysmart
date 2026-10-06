"""Build an unpublished cumulative authoring draft; never changes app packaging.

Usage: python3 prepare_draft.py --source-cache /path/to/verified-pdf-text --inventory
       python3 prepare_draft.py --source-cache /path/to/verified-pdf-text --scaffold

The source cache must contain bbs.txt, bbs.pdf and bca-2025.pdf. Source research
is separate from this script: it never downloads anything or modifies raw data.
Once scaffolded, subject files are edited by authors, not regenerated in place.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import unicodedata

from catalogue import BBS_SOURCE, BBS_YEARS, BCA_SOURCE, revised_bca_subjects

WORKSPACE = Path(__file__).resolve().parents[3]
STAGE1 = WORKSPACE / "Android data/database-stage-1"
STAGE2 = WORKSPACE / "Android data/database-stage-2"
SOURCE_ROOT = WORKSPACE / "android/app/src"
TIMESTAMP = 1791158400000  # 2026-10-05T00:00:00Z, authoring revision timestamp


def normalize(value):
    return " ".join(unicodedata.normalize("NFKC", value).lower().split())


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(",", ":")).encode()).hexdigest()


def stamp(row):
    return dict(row, isActive=True, createdAt=0, updatedAt=0)


def question(qid, chapter, topic, prompt, answer, order):
    return stamp(dict(id=qid, chapterId=chapter, topicId=topic, question=prompt,
                      answer=answer, questionType="written", difficulty="normal",
                      maxScore=2.0, explanation="", sortOrder=order))


def source_bbs_subjects(text):
    """Read printed codes and unit titles; reject missing units rather than guess.

The official PDF repeats MKT 254 verbatim and formats MGT 205 as Roman-numbered
sections. Those two known cases are reconciled explicitly.
"""
    names = {}
    for match in re.finditer(r"^\s*(MGT|ACC|FIN|MKT)\s*(\d{3})\s*:\s*([^\n]+)", text, re.M | re.I):
        code = f"{match[1].upper()} {match[2]}"
        name = re.sub(r"\s{2,}\d+\s*$", "", match[3]).strip()
        names.setdefault(code, name)
    required = {c for rows in BBS_YEARS.values() for c in rows}
    optional = set(names) - required
    # Only the programme's actual concentration papers, not similarly coded core papers.
    optional = {c for c in optional if int(c.split()[1]) >= 250}
    courses = {}
    # The detailed Business English heading omits the code; it starts on PDF page 8.
    current = "MGT 201"
    page = 1
    for raw in text.splitlines(keepends=True):
        page += raw.count("\f")
        line = raw.strip()
        if page < 8:
            continue
        head = re.match(r"(MGT|ACC|FIN|MKT)\s*(\d{3})\s*:?[ \t]+", line, re.I)
        if head:
            current = f"{head[1].upper()} {head[2]}"
        unit = re.match(r"(?:Unit|Chapter)\s*:?\s*(\d+|I)\s*[:.]?\s+(.+)", line, re.I)
        if not unit:
            unit = re.match(r"(?:Unit|Chapter)\s*:?\s*(\d+)\s*[:.]\s*(.+)", line, re.I)
        if not unit and current == "MGT 251":
            # The PDF prints this heading without the word 'Unit'.
            unit = re.match(r"(7):\s*(International Financial Environment.*)", line)
        if unit:
            title = unit[2].strip()
            title = re.split(r"\s{2,}|\s*-{3,}", title)[0].strip().rstrip(":")
            # Some hour allocations have only one separating space.
            title = re.sub(r"\s+(?:LH\s*\d+|\d+\s*(?:LHs?|LHS|Hrs?\.?|marks).*?)$", "", title, flags=re.I).strip()
            if not title:
                raise ValueError(f"Empty BBS unit title: {current} page {page}")
            rows = courses.setdefault(current, {})
            number = 1 if unit[1].upper() == "I" else int(unit[1])
            previous = rows.get(number)
            if previous and previous["title"] != title:
                raise ValueError(f"Conflicting repeated unit: {current} {number}")
            rows.setdefault(number, {"number": number, "title": title, "page": page})
    courses["MGT 205"] = {i: {"number": i, "title": title, "page": 20 if i <= 8 else 21}
                           for i, title in enumerate([
        "The Communication Process", "Business Communication", "Skills and Values in Business Communication",
        "Electronic and Other Messages", "Reports and Proposals", "Oral Communication",
        "Visual Communication", "Employment Communication", "Culture and Society",
        "Money and Management", "Science and Environment", "Gender and Women",
        "Life, Death and Beyond", "Art and Philosophy",
    ], 1)}
    names["MGT 401"] = "Final Project"
    courses["MGT 401"] = {1: {"number": 1, "title": "Fieldwork, Research Report and Viva Preparation", "page": 6}}
    rows = []
    for year in range(1, 5):
        codes = list(BBS_YEARS[year]) + (sorted(optional) if year == 4 else [])
        for code in codes:
            units = courses.get(code, {})
            if not units:
                raise ValueError(f"No detailed BBS units for {code}")
            expected = set(range(1, max(units) + 1))
            # The official Principles of Management text jumps from Unit 7 to
            # Unit 9. Preserve that printed gap; do not fabricate an eighth unit.
            permitted_gap = {8} if code == "MGT 213" else set()
            if expected - set(units) != permitted_gap:
                raise ValueError(f"Missing BBS units for {code}: {sorted(expected - set(units))}")
            titles = [units[i]["title"] for i in sorted(units)]
            # A bare 'Introduction' is clarified by the real subject, not a placeholder.
            titles = [f"Introduction to {names[code]}" if t.lower() == "introduction" else t for t in titles]
            rows.append(dict(courseId="course-bbs", periodNumber=year, code=code, name=names[code],
                             source=BBS_SOURCE, syllabus="TU four-year BBS course structure with details",
                             isElective=code in optional, chapters=titles, chapterNumbers=sorted(units),
                             outlineKind="original-practice" if code == "MGT 401" else "published-unit",
                             sourcePages=sorted({r["page"] for r in units.values()}),
                             notes="Choose three papers from one concentration; alternatives are not compulsory."
                             if code in optional else "Published syllabus skips Unit 8; printed numbering retained." if permitted_gap else ""))
    return rows


def baseline_subjects():
    """Export the literal retained seed rows without changing their source IDs.

These constrained parsers fail inventory-count assertions if seed syntax changes.
They are authoring helpers, not application parsing/business logic.
"""
    seed = (SOURCE_ROOT / "main/java/com/studysmart/app/core/database/DatabaseSeed.kt").read_text()
    docs = {}
    for m in re.finditer(r'insertSubject\(db, "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)", (\d+), timestamp\)', seed):
        sid, period, code, name, order = m.groups()
        if not sid.startswith("subject-bca-"):
            continue
        docs[sid] = dict(schemaVersion=1, contentRevision=2,
                         subject=stamp(dict(id=sid, academicPeriodId=period, code=code, name=name,
                                            description="", sortOrder=int(order))),
                         chapters=[], topics=[], questions=[], questionKeywords=[], provenance=[])
    for m in re.finditer(r'insertChapter\(db, "([^"]+)", "([^"]+)", (\d+), "([^"]+)", (\d+), timestamp\)', seed):
        cid, sid, number, title, order = m.groups()
        if sid in docs:
            docs[sid]["chapters"].append(stamp(dict(id=cid, subjectId=sid, chapterNumber=int(number),
                                                title=title, description="", sortOrder=int(order))))
    chapter_subject = {r["id"]: sid for sid, doc in docs.items() for r in doc["chapters"]}
    for m in re.finditer(r'insertTopic\(db, "([^"]+)", "([^"]+)", "([^"]+)", (\d+), timestamp\)', seed):
        tid, cid, title, order = m.groups()
        if cid in chapter_subject:
            docs[chapter_subject[cid]]["topics"].append(stamp(dict(id=tid, chapterId=cid, title=title,
                                                                description="", sortOrder=int(order))))
    for m in re.finditer(r'insertQuestion\(db, "([^"]+)", "([^"]+)", (null|"[^"]+"), "([^"]+)", "([^"]+)", (\d+), timestamp\)', seed):
        qid, cid, topic, prompt, answer, order = m.groups()
        if cid in chapter_subject:
            docs[chapter_subject[cid]]["questions"].append(question(qid, cid, None if topic == "null" else topic[1:-1], prompt, answer, int(order)))
    question_subject = {r["id"]: sid for sid, doc in docs.items() for r in doc["questions"]}
    for m in re.finditer(r'insertKeyword\(db, "([^"]+)", "([^"]+)", ([\d.]+), (\d+)\)', seed):
        qid, term, weight, order = m.groups()
        if qid in question_subject:
            docs[question_subject[qid]]["questionKeywords"].append(dict(questionId=qid, normalizedKeyword=normalize(term), weight=float(weight), sortOrder=int(order)))
    assert len(docs) == 4 and sum(len(d["chapters"]) for d in docs.values()) == 8
    assert sum(len(d["questions"]) for d in docs.values()) == 5
    samples = (SOURCE_ROOT / "debug/java/com/studysmart/app/core/database/DebugSampleCurriculum.kt").read_text()
    heads = list(re.finditer(r'chapter\("([^"]+)", "([^"]+)", "([^"]+)", listOf\(', samples))
    for index, head in enumerate(heads):
        key, sid, title = head.groups()
        if sid not in docs:
            continue
        block = samples[head.end():heads[index + 1].start() if index + 1 < len(heads) else samples.index(").also { validate(it) }")]
        cid = f"debug-sample-{key}"
        doc = docs[sid]
        doc["chapters"].append(stamp(dict(id=cid, subjectId=sid, chapterNumber=3,
                                        title=title + " — Sample practice",
                                        description="Debug-only sample practice; not official examination material.", sortOrder=3)))
        topic_blocks = list(re.finditer(r'topic\("[^"]+", "([^"]+)", "([^"]+)", """(.*?)"""\)', block, re.S))
        order = 0
        def add_rows(prefix, tid, text):
            nonlocal order
            for number, raw in enumerate([l.strip() for l in text.splitlines() if l.strip()], 1):
                prompt, answer, terms = raw.split(" | ")
                order += 1
                qid = f"{prefix}-q{number:02}"
                doc["questions"].append(question(qid, cid, tid, prompt, answer, order))
                for ki, term in enumerate(terms.split(";"), 1):
                    doc["questionKeywords"].append(dict(questionId=qid, normalizedKeyword=normalize(term), weight=1.0, sortOrder=ki))
        for topic in topic_blocks:
            tk, title, text = topic.groups()
            tid = f"{cid}-{tk}"
            doc["topics"].append(stamp(dict(id=tid, chapterId=cid, title=title, description="", sortOrder=len(topic_blocks[:topic_blocks.index(topic)]) + 1)))
            add_rows(tid, tid, text)
        all_text = re.findall(r'"""(.*?)"""', block, re.S)
        assert len(topic_blocks) == 4 and len(all_text) == 5
        add_rows(cid + "-direct", None, all_text[-1])
        assert order == 42
    assert sum(len(d["questions"]) for d in docs.values()) == 173
    return docs


def load_retained():
    docs = {p.stem: json.loads(p.read_text()) for p in sorted((STAGE1 / "subjects").glob("*.json"))}
    docs.update(baseline_subjects())
    assert len(docs) == 14
    assert sum(len(d["chapters"]) for d in docs.values()) == 81
    assert sum(len(d["questions"]) for d in docs.values()) == 607
    assert sum(q["isActive"] for d in docs.values() for q in d["questions"]) == 606
    return docs


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x") as out:
        out.write(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def outline_provenance(spec, subject_id, chapters):
    notes = "Original practice outline, not an official TU chapter." if spec["outlineKind"] == "original-practice" else "Published TU syllabus unit; practice questions are not official examination questions."
    rows = [dict(entityType="subject", entityId=subject_id, notes=notes,
                 sources=[dict(path=spec["source"], locator="PDF catalogue pages " + ", ".join(map(str, spec["sourcePages"])))])]
    for chapter in chapters:
        if spec["outlineKind"] == "published-unit":
            source = dict(path=spec["source"], locator="Detailed syllabus PDF pages " + ", ".join(map(str, spec.get("outlinePages", spec["sourcePages"]))) + f"; unit/section {chapter['chapterNumber']}")
        else:
            source = dict(path="android/tools/curriculum-data/catalogue.py", locator=f"BCA_ROWS / {spec['code']}; original practice outline {chapter['chapterNumber']}") if spec["courseId"] == "course-bca" else dict(path="android/tools/curriculum-data/prepare_draft.py", locator="Original final-project preparation outline; fieldwork requirement in TU BBS PDF page 6")
        rows.append(dict(entityType="chapter", entityId=chapter["id"], notes=notes, sources=[source]))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-cache", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--inventory", action="store_true")
    mode.add_argument("--scaffold", action="store_true")
    mode.add_argument("--refresh-outline-provenance", action="store_true")
    args = parser.parse_args()
    cache = args.source_cache
    required = [cache / "bbs.pdf", cache / "bbs.txt", cache / "bca-2025.pdf"]
    if not all(p.is_file() for p in required):
        parser.error("Verified source cache requires bbs.pdf, bbs.txt and bca-2025.pdf")
    subjects = revised_bca_subjects() + source_bbs_subjects((cache / "bbs.txt").read_text())
    retained = load_retained()
    provenance = dict(status="unpublished-authoring-draft", minimumActiveQuestionsPerChapter=20,
                      decisions=dict(bcaEdition="revised-2025", laterBcaOutlines="original-practice-authorized",
                                     preserveBca=True, removeOldYearlyAndItsHistory=True),
                      sources=[dict(url=BCA_SOURCE, sha256=hashlib.sha256((cache / "bca-2025.pdf").read_bytes()).hexdigest(),
                                    detailedCoverage="Semesters 1–2 only; subject list covers all eight semesters"),
                               dict(url=BBS_SOURCE, sha256=hashlib.sha256((cache / "bbs.pdf").read_bytes()).hexdigest())],
                      retainedQuestions={q["id"]: fingerprint(q) for d in retained.values() for q in d["questions"]},
                      retainedKeywords={q["id"]: fingerprint([k for k in d["questionKeywords"] if k["questionId"] == q["id"]])
                                        for d in retained.values() for q in d["questions"]},
                      stage1Files={str(p.relative_to(STAGE1)): hashlib.sha256(p.read_bytes()).hexdigest()
                                   for p in sorted(STAGE1.rglob("*.json"))},
                      subjects=subjects)
    if args.inventory:
        write_new(STAGE2 / "provenance.json", provenance)
        print(f"Inventory verified: {len(subjects)} new syllabus subjects; {sum(len(s['chapters']) for s in subjects)} new outlines; 81 retained BCA chapters; 607 retained questions (606 active)")
        return
    recorded = json.loads((STAGE2 / "provenance.json").read_text())
    if args.refresh_outline_provenance:
        if (STAGE2 / "bundle-manifest.json").exists():
            raise ValueError("Cannot refresh a published snapshot")
        for field in ["sources", "retainedQuestions", "retainedKeywords", "stage1Files", "decisions"]:
            if recorded[field] != provenance[field]:
                raise ValueError(f"Preservation/source baseline changed: {field}")
        without_locators = lambda rows: [{k: v for k, v in r.items() if k not in {"sourcePages", "cataloguePages", "outlinePages"}} for r in rows]
        if without_locators(recorded["subjects"]) != without_locators(subjects):
            raise ValueError("Curriculum inventory changed; locator-only refresh rejected")
        for spec in subjects:
            programme = "bca-2025" if spec["courseId"] == "course-bca" else "bbs"
            sid = "subject-" + programme + "-" + spec["code"].lower().replace(" ", "-")
            target = STAGE2 / f"subjects/{sid}.json"
            doc = json.loads(target.read_text())
            doc["provenance"] = [p for p in doc["provenance"] if p["entityType"] not in {"subject", "chapter"}] + outline_provenance(spec, sid, doc["chapters"])
            target.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
        (STAGE2 / "provenance.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
        print("Refreshed verified source locators only; question/keyword records unchanged")
        return
    if recorded != provenance:
        raise ValueError("Sources or preservation baseline changed since inventory; review before scaffolding")
    files = {}
    for sid, doc in retained.items():
        copied = copy.deepcopy(doc)
        copied["contentRevision"] = 2
        files[sid] = copied
    for sort_order, spec in enumerate(subjects, 20):
        programme = "bca-2025" if spec["courseId"] == "course-bca" else "bbs"
        sid = "subject-" + programme + "-" + spec["code"].lower().replace(" ", "-")
        period = f"period-bca-semester-{spec['periodNumber']}" if programme == "bca-2025" else f"period-bbs-year-{spec['periodNumber']}"
        kind = "Original practice outline, not an official TU chapter." if spec["outlineKind"] == "original-practice" else "Published TU syllabus unit; practice questions are not official examination questions."
        description = f"{spec['syllabus']}. {spec['notes']}".strip()
        chapters = []
        for number, title in zip(spec.get("chapterNumbers", range(1, len(spec["chapters"]) + 1)), spec["chapters"]):
            cid = "chapter-" + sid.removeprefix("subject-") + f"-{number:02}"
            chapters.append(stamp(dict(id=cid, subjectId=sid, chapterNumber=number,
                                      title=title + (" — Original practice" if spec["outlineKind"] == "original-practice" else ""),
                                      description=kind, sortOrder=number)))
        files[sid] = dict(schemaVersion=1, contentRevision=2,
                         subject=stamp(dict(id=sid, academicPeriodId=period, code=spec["code"], name=spec["name"], description=description, sortOrder=sort_order)),
                         chapters=chapters, topics=[], questions=[], questionKeywords=[],
                         provenance=outline_provenance(spec, sid, chapters))
    courses = [stamp(dict(id="course-bca", code="BCA", name="Bachelor of Computer Applications", shortName="BCA",
                          structureType="semester", summary="TU revised 2025 practice curriculum; previously installed BCA material retained with original placements.", sortOrder=1)),
               stamp(dict(id="course-bbs", code="BBS", name="Bachelor of Business Studies", shortName="BBS",
                          structureType="year", summary="Tribhuvan University four-year programme; concentration alternatives are optional.", sortOrder=2))]
    periods = []
    for course, count, kind in [("bca", 8, "semester"), ("bbs", 4, "year")]:
        for number in range(1, count + 1):
            periods.append(stamp(dict(id=f"period-{course}-{kind}-{number}", courseId=f"course-{course}", periodNumber=number,
                                     periodType=kind, name=f"{kind.title()} {number}", shortName=f"{'Sem' if kind == 'semester' else 'Year'} {number}", sortOrder=number)))
    inventory = [dict(subjectId=sid, academicPeriodId=doc["subject"]["academicPeriodId"], path=f"subjects/{sid}.json") for sid, doc in files.items()]
    # Preflight all outputs before creating any subject files. Refuse to overwrite authored work.
    targets = [STAGE2 / "curriculum.json"] + [STAGE2 / entry["path"] for entry in inventory]
    if any(p.exists() for p in targets):
        raise ValueError("Draft already scaffolded; refusing to overwrite authored curriculum")
    write_new(STAGE2 / "curriculum.json", dict(schemaVersion=1, contentRevision=2, courses=courses, academicPeriods=periods, subjectFiles=inventory))
    for sid, doc in files.items():
        write_new(STAGE2 / f"subjects/{sid}.json", doc)
    print(f"Unpublished draft scaffolded: {len(files)} subjects; {sum(len(d['chapters']) for d in files.values())} chapters; no bundle manifest or app packaging changes")


if __name__ == "__main__":
    main()
