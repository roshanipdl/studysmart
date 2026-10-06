import json
import hashlib
import unittest
from prepare_stage_one_reviewed import build, OUTPUT, STAGE1, OVERRIDES, EXCLUDED


class ReviewedStageOneTest(unittest.TestCase):
    def test_exact_source_derivation_and_freeze(self):
        files = build()
        self.assertEqual(files, build())
        old_manifest = json.loads((STAGE1 / 'bundle-manifest.json').read_text())
        for r in old_manifest['files']:
            self.assertEqual(r['sha256'], hashlib.sha256((STAGE1 / r['path']).read_bytes()).hexdigest())
        total = active = 0
        for path, data in files.items():
            self.assertEqual(data, (OUTPUT / path).read_bytes())
            if not path.startswith('subjects/'):
                continue
            old = json.loads((STAGE1 / path).read_text())
            new = json.loads(data)
            for original, reviewed in zip(old['questions'], new['questions'], strict=True):
                expected = dict(original)
                if expected['id'] in OVERRIDES:
                    expected['question'] = OVERRIDES[expected['id']]
                if expected['id'] in EXCLUDED:
                    expected['isActive'] = False
                self.assertEqual(expected, reviewed)
                total += 1
                active += reviewed['isActive']
            old['questions'] = new['questions']
            self.assertEqual(old, new)
        self.assertEqual((434, 427), (total, active))
        manifest = json.loads(files['bundle-manifest.json'])
        self.assertEqual('bca-stage1-reviewed', manifest['datasetId'])
        for r in manifest['files']:
            self.assertEqual(r['sha256'], hashlib.sha256(files[r['path']]).hexdigest())
        digest = hashlib.sha256(''.join(f"{r['path']}\t{r['sha256']}\n" for r in manifest['files']).encode()).hexdigest()
        self.assertEqual(digest, manifest['bundleDigest'])
