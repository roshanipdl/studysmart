"""Report authoring progress, never publish an incomplete draft.

--require-complete exits nonzero for any active chapter with fewer than 20 active
questions. A successful informational report is NOT a coverage acceptance pass.
"""
import argparse
import json

from prepare_draft import STAGE2


def coverage():
    catalog = json.loads((STAGE2 / "curriculum.json").read_text())
    result = []
    questions = active = 0
    for entry in catalog["subjectFiles"]:
        doc = json.loads((STAGE2 / entry["path"]).read_text())
        questions += len(doc["questions"])
        active += sum(q["isActive"] for q in doc["questions"])
        for c in doc["chapters"]:
            if not c["isActive"]:
                continue
            count = sum(q["isActive"] and q["chapterId"] == c["id"] for q in doc["questions"])
            result.append(dict(chapterId=c["id"], subjectId=doc["subject"]["id"],
                               title=c["title"], activeQuestions=count, missing=max(0, 20 - count)))
    missing = sum(r["missing"] for r in result)
    return dict(status="complete" if missing == 0 else "incomplete",
                published=False, minimumActiveQuestionsPerChapter=20,
                courses=len(catalog["courses"]), subjects=len(catalog["subjectFiles"]),
                activeChapters=len(result), questions=questions, activeQuestions=active,
                chaptersAtMinimum=sum(r["missing"] == 0 for r in result),
                remainingQuestions=missing, chapters=result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()
    report = coverage()
    (STAGE2 / "curriculum-coverage.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"UNPUBLISHED {report['status'].upper()}: {report['activeQuestions']} active questions; "
          f"{report['chaptersAtMinimum']}/{report['activeChapters']} chapters at 20; "
          f"{report['remainingQuestions']} additional questions required")
    if args.require_complete and report["remainingQuestions"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
