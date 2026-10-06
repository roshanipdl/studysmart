"""Append reviewed rows to the unpublished draft, preserving every existing row.

Run from any directory with Python 3. No fetching, database import or deletion.
Preflight the entire batch before writing any subject. Re-running verifies
identical existing generated IDs and does not append duplicates.
"""
import copy
import json
import os
import re
import tempfile

from practice_questions import ROWS
from prepare_draft import STAGE2, TIMESTAMP, fingerprint, normalize, question


def parsed_rows(chapter_id, text):
    result = []
    for index, line in enumerate([l.strip() for l in text.splitlines() if l.strip()], 1):
        fields = line.split(" | ")
        if len(fields) != 3:
            raise ValueError(f"Malformed practice row: {chapter_id} {index}")
        prompt, answer, raw_terms = fields
        terms = [normalize(t) for t in raw_terms.split(";")]
        if not prompt or not answer or len(terms) < 3 or len(set(terms)) != len(terms):
            raise ValueError(f"Invalid practice evidence: {chapter_id} {index}")
        normalized = normalize(answer)
        if not all(re.search(r"(?<!\w)" + re.escape(t) + r"(?!\w)", normalized) for t in terms):
            raise ValueError(f"Reference answer misses keyword: {chapter_id} {index}")
        result.append((f"practice-v2-{chapter_id}-q{index:02}", prompt, answer, terms))
    return result


def main():
    if (STAGE2 / "bundle-manifest.json").exists():
        raise ValueError("Published snapshots cannot be edited in place")
    catalog = json.loads((STAGE2 / "curriculum.json").read_text())
    docs = {entry["path"]: json.loads((STAGE2 / entry["path"]).read_text()) for entry in catalog["subjectFiles"]}
    originals = copy.deepcopy(docs)
    provenance = json.loads((STAGE2 / "provenance.json").read_text())
    owners = {c["id"]: path for path, d in docs.items() for c in d["chapters"]}
    appended = 0
    for cid, raw in ROWS.items():
        if cid not in owners:
            raise ValueError(f"Unknown practice chapter: {cid}")
        doc = docs[owners[cid]]
        prompts = {normalize(q["question"]) for q in doc["questions"] if q["chapterId"] == cid}
        by_id = {q["id"]: q for q in doc["questions"]}
        order = max((q["sortOrder"] for q in doc["questions"] if q["chapterId"] == cid), default=0)
        for qid, prompt, answer, terms in parsed_rows(cid, raw):
            expected = question(qid, cid, None, prompt, answer, order + 1)
            expected.update(createdAt=TIMESTAMP, updatedAt=TIMESTAMP,
                            explanation="Original StudySmart practice question; not an official TU past-paper question. Keyword match is grading evidence, not semantic correctness.")
            keywords = [dict(questionId=qid, normalizedKeyword=t, weight=1.0, sortOrder=i) for i, t in enumerate(terms, 1)]
            if qid in by_id:
                existing = by_id[qid]
                # Preserve the permanent original ordering on an idempotent rerun.
                expected["sortOrder"] = existing["sortOrder"]
                if existing != expected or [k for k in doc["questionKeywords"] if k["questionId"] == qid] != keywords:
                    raise ValueError(f"Generated ID already exists with different content: {qid}")
                continue
            if normalize(prompt) in prompts:
                raise ValueError(f"Duplicate chapter prompt: {cid}: {prompt}")
            prompts.add(normalize(prompt))
            order += 1
            expected["sortOrder"] = order
            doc["questions"].append(expected)
            doc["questionKeywords"].extend(keywords)
            doc["provenance"].append(dict(entityType="question", entityId=qid,
                                           notes="Original practice; reference answer and keywords reviewed against standard subject concepts.",
                                           sources=[dict(path="android/tools/curriculum-data/practice_questions.py", locator=f"ROWS[{cid}]/{qid.rsplit('-q', 1)[1]}")]))
            appended += 1
        for q in doc["questions"]:
            if q["id"] in provenance["retainedQuestions"]:
                assert fingerprint(q) == provenance["retainedQuestions"][q["id"]]
                assert fingerprint([k for k in doc["questionKeywords"] if k["questionId"] == q["id"]]) == provenance["retainedKeywords"][q["id"]]
    # JSON authoring outputs only; this never opens or mutates an installed Room DB.
    changed = [path for path, doc in docs.items() if doc != originals[path]]
    for path in changed:
        target = STAGE2 / path
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=target.parent,
                                             prefix=".practice-", suffix=".tmp", delete=False) as output:
                temporary = output.name
                output.write(json.dumps(docs[path], ensure_ascii=False, indent=2) + "\n")
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, target)
        finally:
            if temporary is not None and os.path.exists(temporary):
                os.unlink(temporary)
    print(f"Appended {appended} original practice questions to {len(changed)} draft subject files")


if __name__ == "__main__":
    main()
