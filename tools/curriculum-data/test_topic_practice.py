import json
import re
import unittest
from unittest.mock import patch
from copy import deepcopy

from fill_topic_practice import build, sentences, tokens
from prepare_draft import STAGE2, normalize, fingerprint


class TopicPracticeTest(unittest.TestCase):
    def test_generation_from_incomplete_fixture_preserves_existing_and_is_idempotent(self):
        path = 'subjects/math.json'
        original = dict(id='old', chapterId='math-unit', question='Existing question?', answer='Existing answer.', isActive=True, sortOrder=1)
        doc = dict(subject=dict(id='subject-bca-math', name='Mathematics-I'),
                   chapters=[dict(id='math-unit', title='Matrices and Determinants')],
                   questions=[original], questionKeywords=[], provenance=[])
        texts = [
            'A matrix is a rectangular arrangement of numbers in rows and columns, and its dimensions specify the number of rows and columns.',
            'Matrix multiplication requires the number of columns of the first matrix to equal the number of rows of the second matrix.',
            'The determinant of a two by two matrix with rows a b and c d is the value ad minus bc.',
        ]
        pool = [(s, tokens(s), 'android/tools/curriculum-data/recall_facts.txt', 'Original math fact') for s in texts]
        pool.append(('JavaScript functions use arrow syntax for concise callbacks in browser programs.', tokens('JavaScript functions use arrow syntax for concise callbacks in browser programs.'), 'data/programming-guide.md', 'Wrong domain'))
        docs = {path: deepcopy(doc)}
        with patch('fill_topic_practice.corpus', return_value=pool):
            self.assertEqual(19, build(docs))
            self.assertEqual(original, docs[path]['questions'][0])
            self.assertEqual(20, len(docs[path]['questions']))
            self.assertTrue(all('JavaScript' not in q['answer'] for q in docs[path]['questions']))
            self.assertEqual(19, len(docs[path]['questionKeywords']))
            self.assertEqual(19, len(docs[path]['provenance']))
            snapshot = deepcopy(docs)
            self.assertEqual(0, build(docs))
            self.assertEqual(snapshot, docs)

    def test_insufficient_source_is_rejected_and_corrupt_statements_filtered(self):
        docs = {'x': dict(subject=dict(id='subject-math', name='Mathematics-I'), chapters=[dict(id='c', title='Matrices')], questions=[], questionKeywords=[], provenance=[])}
        with patch('fill_topic_practice.corpus', return_value=[]):
            with self.assertRaisesRegex(ValueError, 'Insufficient related'):
                build(docs)
        self.assertEqual([], list(sentences('Ifyou use a matrix, it can represent a relation when if @ ~ band b ~ c.')))

    def test_full_draft_coverage_evidence_and_preservation(self):
        catalog = json.loads((STAGE2 / "curriculum.json").read_text())
        docs = {e["path"]: json.loads((STAGE2 / e["path"]).read_text()) for e in catalog["subjectFiles"]}
        before = {p: fingerprint(d) for p, d in docs.items()}
        generated = 0
        ids = set()
        for doc in docs.values():
            keywords = {}
            for k in doc["questionKeywords"]:
                keywords.setdefault(k["questionId"], []).append(k)
            evidence = {p["entityId"]: p for p in doc["provenance"]}
            for chapter in doc["chapters"]:
                questions = [q for q in doc["questions"] if q["chapterId"] == chapter["id"] and q["isActive"]]
                self.assertGreaterEqual(len(questions), 20, chapter["id"])
                # Existing duplicate past-paper prompts are retained by contract.
                prompts = [normalize(q["question"]) for q in questions if q["id"].startswith("topic-recall-v2-")]
                self.assertEqual(len(prompts), len(set(prompts)), chapter["id"])
                old_prompts = {normalize(q["question"]) for q in questions if not q["id"].startswith("topic-recall-v2-")}
                self.assertFalse(set(prompts) & old_prompts, chapter["id"])
            for q in doc["questions"]:
                self.assertNotIn(q["id"], ids)
                ids.add(q["id"])
                if not q["id"].startswith("topic-recall-v2-"):
                    continue
                generated += 1
                self.assertIn("_____", q["question"])
                self.assertIn("editorial review pending", q["explanation"])
                self.assertTrue(evidence[q["id"]]["sources"])
                self.assertEqual(1, len(keywords[q["id"]]))
                term = keywords[q["id"]][0]["normalizedKeyword"]
                self.assertRegex(normalize(q["answer"]), r"(?<!\w)" + re.escape(term) + r"(?!\w)")
        self.assertEqual(12025, generated)
        self.assertEqual(0, build(docs))
        self.assertEqual(before, {p: fingerprint(d) for p, d in docs.items()})


if __name__ == "__main__":
    unittest.main()
