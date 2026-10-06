"""Fill the unpublished draft with lightweight, source-backed recall practice.

No web access, model calls, app import, or changes to existing question records.
Questions ask learners to recall a missing phrase from a topic-related statement.
Answers retain the complete source statement, not invented examination answers.
These automatically selected practice items still require editorial review.
"""
import argparse
import collections
import hashlib
import json
import math
import os
from pathlib import Path
import re
import tempfile

from prepare_draft import STAGE2, WORKSPACE, TIMESTAMP, normalize, fingerprint, question

STOP = set("a an the and or of to in on at for from by with as is are was were be been being this that these those it its their which what how why when where can could should may must will would has have had do does did not into also such each using use used introduction fundamentals basic advanced original practice system systems management business computer unit chapter concepts principles application applications".split())
STOP -= {"management", "business", "computer", "system", "systems"}


def tokens(text):
    return {w for w in re.findall(r"[a-z][a-z0-9_-]{2,}", normalize(text)) if w not in STOP}


def root(word):
    aliases = {"selling": "sale", "sales": "sale", "banking": "bank", "banks": "bank", "advertising": "advertisement", "advertisements": "advertisement", "english": "language", "macroeconomics": "economic", "microeconomics": "economic", "economics": "economic", "entrepreneurship": "entrepreneur", "entrepreneurs": "entrepreneur"}
    if word in aliases:
        return aliases[word]
    if word in {"anti-derivative", "antiderivative", "integral", "integrals", "integration"}:
        return "integration"
    if word == "matrices":
        return "matrix"
    if word.endswith("ies"):
        return word[:-3] + "y"
    return word[:-1] if word.endswith("s") and not word.endswith("ss") else word


def sentences(text):
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    for paragraph in re.split(r"\n\s*\n", text):
        if "|" in paragraph or paragraph.lstrip().startswith(("#", "http", "Source:", "Sources:")):
            continue
        paragraph = re.sub(r"[*_`#]", "", paragraph)
        paragraph = " ".join(paragraph.split())
        for statement in re.split(r"(?<=[.!?])\s+(?=[A-Z])", paragraph):
            statement = re.sub(r"^\s*(?:[-•]+|\d+[.)])\s*", "", statement).strip()
            words = statement.split()
            if not 12 <= len(words) <= 85 or not statement.endswith((".", "!")):
                continue
            if re.search(r"https?://|\[.*?\]|©|copyright|OCR|question bank|this guide|source coverage", statement, re.I):
                continue
            if re.search(r"[@~]|\b(?:Ifyou|isused|ofthe|inthe|tothe|andthe)\b|[A-Za-z][0-9][A-Za-z]", statement):
                continue
            if not re.search(r"\b(is|are|means|provides|uses|allows|requires|includes|refers|helps|represents|contains|reduces|supports|defines|must|can|should)\b", statement, re.I):
                continue
            yield statement


def corpus(docs):
    frozen = STAGE2 / "recall-source-corpus.json"
    if frozen.exists():
        return [(r[0], set(r[1]), r[2], r[3]) for r in json.loads(frozen.read_text())]
    rows = {}
    def add(text, path, locator):
        for sentence in sentences(text):
            rows.setdefault(normalize(sentence), (sentence, tokens(sentence), path, locator))
    facts = Path(__file__).with_name("recall_facts.txt")
    for number, line in enumerate(facts.read_text().splitlines(), 1):
        add(line, "android/tools/curriculum-data/recall_facts.txt", f"Original practice fact {number}")
    for path, doc in docs.items():
        for q in doc["questions"]:
            if not q["id"].startswith("topic-recall-v2-"):
                add(q["answer"], "Android data/database-stage-2/" + path, q["id"])
    for path in sorted((WORKSPACE / "data/BCA").rglob("*.md")):
        if "/dbms/" in str(path) or not any(s in path.name for s in ("guide", "qa")):
            continue
        add(path.read_text(errors="replace"), str(path.relative_to(WORKSPACE)), "Topic-related explanatory statement")
    syllabus = Path('/tmp/studysmart-syllabi-5hesuhov/bbs.txt')
    if syllabus.exists():
        add(syllabus.read_text(), "https://www.fomecd.edu.np/documents/4%20Year%20BBS%20Course%20Structre%20with%20Details.pdf", "Published explanatory course text")
    result = list(rows.values())
    frozen.write_text(json.dumps([(s, sorted(w), p, l) for s, w, p, l in result], ensure_ascii=False, indent=2) + "\n")
    return result


def build(docs):
    pool = corpus(docs)
    frequencies = collections.Counter(t for _, words, _, _ in pool for t in words)
    weights = {t: math.log(1 + len(pool) / count) for t, count in frequencies.items()}
    appended = 0
    for path, doc in docs.items():
        subject_words = tokens(doc["subject"]["name"])
        mathematics = bool(subject_words & {"mathematics-i", "mathematics-ii", "discrete", "numerical"})
        bbs = "bbs" in doc["subject"]["id"]
        for chapter in doc["chapters"]:
            existing = [q for q in doc["questions"] if q["chapterId"] == chapter["id"]]
            deficit = max(0, 20 - sum(q["isActive"] for q in existing))
            if not deficit:
                continue
            topic_words = tokens(chapter["title"])
            # Distinct source statements, rather than twenty rewordings of one fact.
            ranked = []
            for sentence, words, source, locator in pool:
                if mathematics:
                    if not source.endswith("recall_facts.txt") or not words & {"matrix", "matrices", "vector", "sequence", "series", "permutation", "derivative", "integration", "equation", "limit", "continuous", "numerical", "geometry", "set", "relation", "induction", "graph", "algebraic", "boolean"}:
                        continue
                elif bbs and not ("fomecd.edu.np" in source or source.endswith("recall_facts.txt") or "subject-bbs-" in source):
                    continue
                topic_overlap = {w for w in words if root(w) in {root(t) for t in topic_words}}
                subject_overlap = {w for w in words if root(w) in {root(t) for t in subject_words}}
                if not topic_overlap and not subject_overlap:
                    if mathematics:
                        topic_overlap = words & {"matrix", "vector", "sequence", "derivative", "integration", "set", "relation", "graph", "numerical"}
                    else:
                        continue
                if not topic_overlap and not subject_overlap:
                    continue
                score = sum(weights[t] for t in topic_overlap) * 3 + sum(weights[t] for t in subject_overlap)
                eligible_terms = sorted(words - topic_words, key=lambda t: (-weights[t], t))[:10]
                for variant, term in enumerate(eligible_terms):
                    ranked.append((variant, -score, hashlib.sha256((sentence + term).encode()).hexdigest(), sentence, words, source, locator, term))
            ranked.sort()
            if len(ranked) < deficit:
                raise ValueError(f"Insufficient related source statements for {chapter['id']}: {len(ranked)} available, {deficit} needed")
            prompts = {normalize(q["question"]) for q in existing}
            order = max((q["sortOrder"] for q in existing), default=0)
            added = 0
            for _, _, digest, sentence, words, source, locator, selected_term in ranked:
                eligible = [(m, weights.get(normalize(m.group()), 0)) for m in re.finditer(r"\b[A-Za-z][A-Za-z0-9_-]{3,}\b", sentence)
                            if normalize(m.group()) == selected_term]
                if not eligible:
                    continue
                match, _ = max(eligible, key=lambda pair: (pair[1], len(pair[0].group())))
                missing = match.group()
                masked = sentence[:match.start()] + "_____" + sentence[match.end():]
                prompt = f"{chapter['title'].replace(' — Original practice', '')}: Complete the statement with the missing term.\n\n{masked}"
                if normalize(prompt) in prompts:
                    continue
                qid = f"topic-recall-v2-{chapter['id']}-{digest[:12]}"
                answer = f"Missing term: {missing}.\n\n{sentence}"
                order += 1
                row = question(qid, chapter["id"], None, prompt, answer, order)
                row.update(createdAt=TIMESTAMP, updatedAt=TIMESTAMP,
                           explanation="Generated topic-related recall practice, automatically selected from existing explanatory material. Not an official TU question; editorial review pending. Keyword matching does not establish semantic correctness.")
                doc["questions"].append(row)
                term = normalize(missing)
                doc["questionKeywords"].append(dict(questionId=qid, normalizedKeyword=term, weight=1.0, sortOrder=1))
                doc["provenance"].append(dict(entityType="question", entityId=qid,
                                               notes="Automatically selected extractive recall practice; verify topical fit before production publication.",
                                               sources=[dict(path=source, locator=locator)]))
                prompts.add(normalize(prompt))
                added += 1
                appended += 1
                if added == deficit:
                    break
            if added != deficit:
                raise ValueError(f"Insufficient distinct recall prompts: {chapter['id']}")
    return appended


def main():
    if (STAGE2 / "bundle-manifest.json").exists():
        raise ValueError("Refusing to modify a published snapshot")
    catalog = json.loads((STAGE2 / "curriculum.json").read_text())
    docs = {e["path"]: json.loads((STAGE2 / e["path"]).read_text()) for e in catalog["subjectFiles"]}
    before = {path: fingerprint(doc) for path, doc in docs.items()}
    preserved = {q["id"]: fingerprint(q) for doc in docs.values() for q in doc["questions"]}
    count = build(docs)
    for doc in docs.values():
        for q in doc["questions"]:
            if q["id"] in preserved:
                assert fingerprint(q) == preserved[q["id"]]
    for relative, doc in docs.items():
        if fingerprint(doc) == before[relative]:
            continue
        path = STAGE2 / relative
        with tempfile.NamedTemporaryFile("w", dir=path.parent, encoding="utf-8", delete=False) as out:
            out.write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
            temporary = out.name
        os.replace(temporary, path)
    print(f"Added {count} topic-related recall questions; previous records preserved")


if __name__ == "__main__":
    main()
