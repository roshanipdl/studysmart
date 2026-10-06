import json
import shutil
import subprocess
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import append_practice
from append_practice import parsed_rows
from practice_questions import ROWS
from prepare_draft import STAGE2, normalize
from report_coverage import coverage


class PracticeQuestionTest(unittest.TestCase):
    def test_reviewed_rows_have_distinct_prompts_and_answer_keyword_evidence(self):
        counts = []
        for cid, text in ROWS.items():
            rows = parsed_rows(cid, text)
            counts.append(len(rows))
            prompts = [normalize(r[1]) for r in rows]
            self.assertEqual(len(prompts), len(set(prompts)))
        self.assertEqual([17, 20, 20, 20], counts)
        self.assertEqual(77, sum(counts))

    def test_reference_answer_missing_a_keyword_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "misses keyword"):
            parsed_rows("example", "Question? | A database stores data. | database;data;missing")

    def test_rerunning_practice_append_does_not_change_any_subject(self):
        with tempfile.TemporaryDirectory(prefix="studysmart-practice-fixture-") as root:
            fixture = Path(root) / "draft"
            shutil.copytree(STAGE2, fixture)
            (fixture / "bundle-manifest.json").unlink(missing_ok=True)
            # Exercise a first append as well as the identical rerun in an isolated copy.
            for path in (fixture / "subjects").glob("*.json"):
                doc = json.loads(path.read_text())
                doc["questions"] = [q for q in doc["questions"] if not q["id"].startswith("practice-v2-")]
                doc["questionKeywords"] = [k for k in doc["questionKeywords"] if not k["questionId"].startswith("practice-v2-")]
                doc["provenance"] = [p for p in doc["provenance"] if not p["entityId"].startswith("practice-v2-")]
                path.write_text(json.dumps(doc))
            with patch.object(append_practice, "STAGE2", fixture):
                append_practice.main()
                documents = [json.loads(p.read_text()) for p in (fixture / "subjects").glob("*.json")]
                self.assertEqual(77, sum(q["id"].startswith("practice-v2-") for d in documents for q in d["questions"]))
                paths = list((fixture / "subjects").glob("*.json"))
                before = {p: p.read_bytes() for p in paths}
                append_practice.main()
                self.assertEqual(before, {p: p.read_bytes() for p in paths})

    def test_conflicting_generated_id_fails_before_writing_fixture_files(self):
        with tempfile.TemporaryDirectory(prefix="studysmart-practice-conflict-") as root:
            fixture = Path(root) / "draft"
            shutil.copytree(STAGE2, fixture)
            (fixture / "bundle-manifest.json").unlink(missing_ok=True)
            path = fixture / "subjects/subject-bca-s4-dbms.json"
            doc = json.loads(path.read_text())
            next(q for q in doc["questions"] if q["id"].startswith("practice-v2-"))["answer"] = "Conflicting content"
            path.write_text(json.dumps(doc))
            paths = list((fixture / "subjects").glob("*.json"))
            before = {p: p.read_bytes() for p in paths}
            with patch.object(append_practice, "STAGE2", fixture):
                with self.assertRaisesRegex(ValueError, "different content"):
                    append_practice.main()
            self.assertEqual(before, {p: p.read_bytes() for p in paths})

    def test_append_rejects_published_snapshot(self):
        with tempfile.TemporaryDirectory(prefix="studysmart-published-fixture-") as root:
            fixture = Path(root)
            (fixture / "bundle-manifest.json").write_text("{}")
            with patch.object(append_practice, "STAGE2", fixture):
                with self.assertRaisesRegex(ValueError, "Published snapshots"):
                    append_practice.main()

    def test_completed_chapters_have_twenty_and_unfinished_coverage_is_honest(self):
        report = coverage()
        counts = {c["chapterId"]: c["activeQuestions"] for c in report["chapters"]}
        for cid in ROWS:
            self.assertEqual(20, counts[cid])
        self.assertEqual(12709, report["questions"])
        self.assertEqual(12708, report["activeQuestions"])
        self.assertEqual(631, report["chaptersAtMinimum"])
        self.assertEqual("complete", report["status"])
        self.assertFalse(report["published"])
        self.assertEqual(0, report["remainingQuestions"])

    @unittest.skipUnless(shutil.which("cc"), "Existing C compiler unavailable; reference-code check not run")
    def test_c_reference_expressions_on_existing_c11_compiler(self):
        source = r'''
#include <limits.h>
#include <stdio.h>
int main(void) {
    int a = 0, b = 0;
    a = b = 5;
    printf("%d %.1f %d %d %d %d %d %d %d %zu %d %zu\n",
           7 / 2, 7.0 / 2, -7 / 3, -7 % 3, (int)3.9,
           2 + 3 * 4, (2 + 3) * 4, a, b, sizeof(char),
           _Generic('A', int: 1, default: 0), sizeof("A"));
    unsigned int maximum = UINT_MAX;
    printf("%u\n", maximum + 1u);
    return 0;
}
'''
        with tempfile.TemporaryDirectory(prefix="studysmart-c-reference-") as root:
            path = Path(root)
            (path / "reference.c").write_text(source)
            compiled = subprocess.run([shutil.which("cc"), "-std=c11", "-Wall", "-Wextra", "-Werror",
                                       str(path / "reference.c"), "-o", str(path / "reference")],
                                      capture_output=True, text=True, timeout=20)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            result = subprocess.run([str(path / "reference")], capture_output=True, text=True, timeout=5)
            self.assertEqual(0, result.returncode)
            self.assertEqual("3 3.5 -2 -1 3 14 20 5 5 1 1 2\n0\n", result.stdout)


if __name__ == "__main__":
    unittest.main()
