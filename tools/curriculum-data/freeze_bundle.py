"""Freeze the validated Stage 2 debug snapshot; never rewrite an existing freeze."""
import hashlib
import json
import unittest

from prepare_draft import STAGE2
from report_coverage import coverage


def manifest(root):
    catalog = json.loads((root / "curriculum.json").read_text())
    paths = ["curriculum.json"] + [e["path"] for e in catalog["subjectFiles"]]
    if len(paths) != len(set(paths)):
        raise ValueError("Duplicate bundle path")
    rows = []
    for path in sorted(paths):
        import re
        if path != "curriculum.json" and not re.fullmatch(r"subjects/[A-Za-z0-9_-]+\.json", path):
            raise ValueError("Unsafe bundle path")
        file = root / path
        if file.is_symlink() or not file.resolve().is_relative_to(root.resolve()):
            raise ValueError("Unsafe bundle file")
        rows.append(dict(path=path, sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
    digest = hashlib.sha256("".join(f"{r['path']}\t{r['sha256']}\n" for r in rows).encode()).hexdigest()
    return dict(datasetId="bca-local-curriculum", contentVersion=2, schemaVersion=1,
                bundleDigest=digest, files=rows)


def main():
    suite = unittest.defaultTestLoader.loadTestsFromNames(["test_authoring", "test_practice", "test_topic_practice"])
    if not unittest.TextTestRunner(verbosity=1).run(suite).wasSuccessful():
        raise SystemExit("Authoring verification failed; refusing freeze")
    if coverage()["remainingQuestions"]:
        raise SystemExit("Incomplete curriculum; refusing freeze")
    expected = manifest(STAGE2)
    target = STAGE2 / "bundle-manifest.json"
    if target.exists():
        if json.loads(target.read_text()) != expected:
            raise SystemExit("Frozen snapshot changed; refusing overwrite")
    else:
        with target.open("x") as out:
            out.write(json.dumps(expected, indent=2) + "\n")
    print(f"Verified frozen debug bundle: {len(expected['files'])} files; {expected['bundleDigest']}")


if __name__ == "__main__":
    main()
