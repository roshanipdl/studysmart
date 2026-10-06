import json
import unittest

from catalogue import BBS_YEARS, revised_bca_subjects
from prepare_draft import STAGE1, STAGE2, fingerprint, load_retained, source_bbs_subjects


class AuthoringInventoryTest(unittest.TestCase):
    def test_revised_bca_has_real_subjects_for_all_eight_semesters(self):
        subjects = revised_bca_subjects()
        self.assertEqual(58, len(subjects))
        self.assertEqual(set(range(1, 9)), {s["periodNumber"] for s in subjects})
        self.assertEqual(58, len({(s["periodNumber"], s["code"]) for s in subjects}))
        for subject in subjects:
            self.assertTrue(subject["name"].strip())
            self.assertTrue(all(t.strip() for t in subject["chapters"]))
            self.assertEqual("published-unit" if subject["periodNumber"] <= 2 else "original-practice", subject["outlineKind"])
            if subject["periodNumber"] <= 2:
                self.assertTrue(subject["outlinePages"])
                self.assertGreaterEqual(min(subject["outlinePages"]), 10)
            else:
                self.assertEqual([], subject["outlinePages"])
        self.assertEqual(16, sum(s["isElective"] for s in subjects))

    def test_actual_retained_seed_inventory_and_stage_one_are_preserved(self):
        retained = load_retained()
        self.assertEqual(14, len(retained))
        self.assertEqual(81, sum(len(d["chapters"]) for d in retained.values()))
        self.assertEqual(607, sum(len(d["questions"]) for d in retained.values()))
        self.assertEqual(606, sum(q["isActive"] for d in retained.values() for q in d["questions"]))
        for path in (STAGE1 / "subjects").glob("*.json"):
            self.assertEqual(json.loads(path.read_text()), retained[path.stem])
        self.assertTrue(all("year1" not in q["id"] for d in retained.values() for q in d["questions"]))

    def test_recorded_inventory_fingerprints_match_retained_records(self):
        provenance = json.loads((STAGE2 / "provenance.json").read_text())
        for document in load_retained().values():
            for question in document["questions"]:
                self.assertEqual(fingerprint(question), provenance["retainedQuestions"][question["id"]])
                keys = [k for k in document["questionKeywords"] if k["questionId"] == question["id"]]
                self.assertEqual(fingerprint(keys), provenance["retainedKeywords"][question["id"]])

    def bbs_fixture(self):
        codes = [c for rows in BBS_YEARS.values() for c in rows if c != "MGT 401"]
        names = "\n".join(f"{code}: Test Paper {code}" for code in codes)
        text = names + "\f" * 7
        for code in codes:
            text += f"\n{code}: Test Paper {code}\n"
            numbers = [1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12] if code == "MGT 213" else [1, 2, 3]
            for number in numbers:
                label = "Unit I" if number == 1 else f"Chapter {number}"
                text += f"{label}: Topic {number}                         10 LHs\n"
        return text

    def test_bbs_accepts_printed_unit_and_chapter_headings_and_known_gap(self):
        subjects = source_bbs_subjects(self.bbs_fixture())
        self.assertEqual(18, len(subjects))
        self.assertEqual(set(range(1, 5)), {s["periodNumber"] for s in subjects})
        management = next(s for s in subjects if s["code"] == "MGT 213")
        self.assertNotIn(8, management["chapterNumbers"])
        communication = next(s for s in subjects if s["code"] == "MGT 205")
        self.assertEqual(14, len(communication["chapters"]))

    def test_bbs_unknown_missing_unit_is_rejected(self):
        text = self.bbs_fixture().replace("Chapter 2: Topic 2                         10 LHs\n", "", 1)
        with self.assertRaisesRegex(ValueError, "Missing BBS units"):
            source_bbs_subjects(text)

    def test_bbs_conflicting_repeated_unit_is_rejected(self):
        text = self.bbs_fixture() + "\nMGT 201: Business English\nUnit 1: Different Topic            10 LHs\n"
        with self.assertRaisesRegex(ValueError, "Conflicting repeated unit"):
            source_bbs_subjects(text)


class DraftCatalogueTest(unittest.TestCase):
    def test_draft_hierarchy_identity_and_retained_content(self):
        catalog = json.loads((STAGE2 / "curriculum.json").read_text())
        courses = {r["id"]: r for r in catalog["courses"]}
        self.assertEqual({"course-bca", "course-bbs"}, set(courses))
        periods = {r["id"]: r for r in catalog["academicPeriods"]}
        self.assertEqual(8, sum(p["courseId"] == "course-bca" for p in periods.values()))
        self.assertEqual(4, sum(p["courseId"] == "course-bbs" for p in periods.values()))
        ids = set(courses) | set(periods)
        natural_subjects = set()
        chapters_count = 0
        retained = load_retained()
        provenance = json.loads((STAGE2 / "provenance.json").read_text())
        seen_retained = set()
        self.assertEqual(110, len(catalog["subjectFiles"]))
        for entry in catalog["subjectFiles"]:
            self.assertRegex(entry["path"], r"^subjects/[A-Za-z0-9_-]+\.json$")
            doc = json.loads((STAGE2 / entry["path"]).read_text())
            subject = doc["subject"]
            self.assertEqual(entry["subjectId"], subject["id"])
            self.assertEqual(entry["academicPeriodId"], subject["academicPeriodId"])
            self.assertIn(subject["academicPeriodId"], periods)
            natural = (subject["academicPeriodId"], subject["code"])
            self.assertNotIn(natural, natural_subjects)
            natural_subjects.add(natural)
            rows = [subject] + doc["chapters"] + doc["topics"] + doc["questions"]
            for row in rows:
                self.assertRegex(row["id"], r"^[A-Za-z0-9_-]+$")
                self.assertNotIn(row["id"], ids)
                ids.add(row["id"])
                self.assertGreater(row["sortOrder"], 0)
                self.assertGreaterEqual(row["updatedAt"], row["createdAt"])
            chapters = {r["id"]: r for r in doc["chapters"]}
            self.assertEqual(len(chapters), len({r["chapterNumber"] for r in chapters.values()}))
            chapters_count += len(chapters)
            for chapter in chapters.values():
                self.assertEqual(subject["id"], chapter["subjectId"])
                self.assertTrue(chapter["title"].strip())
            topics = {r["id"]: r for r in doc["topics"]}
            for topic in topics.values():
                self.assertIn(topic["chapterId"], chapters)
            questions = {r["id"]: r for r in doc["questions"]}
            for q in questions.values():
                self.assertIn(q["chapterId"], chapters)
                if q["topicId"] is not None:
                    self.assertIn(q["topicId"], topics)
                    self.assertEqual(q["chapterId"], topics[q["topicId"]]["chapterId"])
                self.assertEqual("written", q["questionType"])
                self.assertTrue(q["question"].strip() and q["answer"].strip())
                self.assertGreater(q["maxScore"], 0)
                if q["id"] in provenance["retainedQuestions"]:
                    self.assertEqual(provenance["retainedQuestions"][q["id"]], fingerprint(q))
                    self.assertEqual(provenance["retainedKeywords"][q["id"]], fingerprint([k for k in doc["questionKeywords"] if k["questionId"] == q["id"]]))
                    seen_retained.add(q["id"])
            self.assertEqual(len(doc["questionKeywords"]), len({(k["questionId"], k["normalizedKeyword"]) for k in doc["questionKeywords"]}))
            for k in doc["questionKeywords"]:
                self.assertIn(k["questionId"], questions)
                self.assertGreater(k["weight"], 0)
        self.assertEqual(631, chapters_count)
        self.assertEqual(set(provenance["retainedQuestions"]), seen_retained)
        if (STAGE2 / "bundle-manifest.json").exists():
            from freeze_bundle import manifest
            self.assertEqual(manifest(STAGE2), json.loads((STAGE2 / "bundle-manifest.json").read_text()))
            from report_coverage import coverage
            self.assertEqual(0, coverage()["remainingQuestions"])

    def test_source_stage_one_files_have_not_changed(self):
        import hashlib
        p = json.loads((STAGE2 / "provenance.json").read_text())
        for relative, expected in p["stage1Files"].items():
            self.assertEqual(expected, hashlib.sha256((STAGE1 / relative).read_bytes()).hexdigest(), relative)

    def test_new_outlines_have_locatable_and_honest_chapter_provenance(self):
        for spec in json.loads((STAGE2 / "provenance.json").read_text())["subjects"]:
            programme = "bca-2025" if spec["courseId"] == "course-bca" else "bbs"
            sid = "subject-" + programme + "-" + spec["code"].lower().replace(" ", "-")
            doc = json.loads((STAGE2 / f"subjects/{sid}.json").read_text())
            chapter_evidence = {p["entityId"]: p for p in doc["provenance"] if p["entityType"] == "chapter"}
            self.assertEqual({c["id"] for c in doc["chapters"]}, set(chapter_evidence))
            for entry in chapter_evidence.values():
                if spec["outlineKind"] == "original-practice":
                    self.assertIn("not an official", entry["notes"])
                    self.assertIn("original", entry["sources"][0]["locator"].lower())
                else:
                    self.assertIn("Detailed syllabus", entry["sources"][0]["locator"])
                    if programme == "bca-2025":
                        for page in spec["outlinePages"]:
                            self.assertIn(str(page), entry["sources"][0]["locator"])


if __name__ == "__main__":
    unittest.main()
