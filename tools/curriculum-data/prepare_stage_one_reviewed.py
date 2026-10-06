"""Deterministic Stage-1-only editorial derivative; never modifies the source."""
import hashlib
import json
from pathlib import Path
from prepare_draft import STAGE1

OUTPUT = STAGE1.parent / "database-stage-1-reviewed"
EXCLUDED = {"question-bca-s4-dbms-012", "question-bca-s4-dbms-014", "question-bca-s4-dbms-018", "question-bca-s4-dbms-023", "question-bca-s4-dbms-035", "question-bca-s7-spm-046"}
OVERRIDES = {
"question-bca-s4-dbms-002": "Define a flat-file system. Discuss the merits and demerits of a DBMS compared with traditional file-based storage.",
"question-bca-s4-dbms-013": "Explain selection and projection. Using Student and Department joined on Did, write relational algebra returning Fname for Bachelor students living in Birgunj whose department head is Ramesh Sharma.",
"question-bca-s4-dbms-015": "Differentiate DDL and DML. Given Borrower(Customer name, Loan number) and Loan(Loan number, Branch name, Amount), write relational algebra finding customers who borrowed from branch B1.",
"question-bca-s7-database-administration-030": "Define privileges and explain system and object privileges. Write Oracle commands to create common/local users, create and grant a role with CREATE SESSION and CREATE VIEW, give unlimited users-tablespace quota, and create/assign a profile limiting failed logins to 2, idle time to 5 minutes and concurrent sessions to 3.",
"question-bca-s7-database-administration-042": "Write Oracle commands to create tblbank(acc_no, name, address, ph_no, balance); add an acc_no primary key and balance > 300 check; rename it Bank; insert rows and delete one; export with expdp; grant SELECT/UPDATE to Mohan; add loan_id referencing loan(loan_id); increase name length; and rename name to cname.",
"question-bca-s7-database-administration-045": "Write Oracle commands to create emp(emp_id, name, address, dob, salary); add an emp_id primary key and salary > 10000 check; rename it employee; insert rows and delete one; export with expdp; grant SELECT/UPDATE to User1; add department_id referencing department(department_id); increase name length; and rename name to ename.",
"question-bca-s5-dnt-016": "Define a jagged array. Write C# code displaying its row count and each row’s length, then demonstrate multiplication of compatible 2×2 and 2×3 matrices.",
"question-bca-s5-dnt-021": "Explain properties and automatic properties in C#. Explain how the this keyword declares an indexer, with an example.",
"question-bca-s7-na-019": "Explain the access, distribution and core layers of network infrastructure. Differentiate logical and physical network diagrams, then compare on-premises, colocation, cloud and hybrid enterprise datacenter strategies.",
"question-bca-s8-ms-004": "Define MIDI and compare it with digital audio. Explain how sampling rate, bit depth, channels and duration determine uncompressed audio file size, and demonstrate the calculation with an example.",
"question-bca-s5-mise-036": "Differentiate technical and non-technical attacks on e-commerce systems, with examples.",
"question-bca-s5-dnt-002": "Define managed code. Explain how CIL supports execution on compatible runtimes and how CTS/CLS enable language interoperability in .NET.",
"question-bca-s5-dnt-024": "Define inheritance. Write C# code demonstrating multilevel class inheritance and multiple interface implementation; explain why multiple class inheritance is unsupported.",
"question-bca-s5-dnt-028": "Define generics in C#. Demonstrate a generic class with a program and list common generic collection classes.",
"question-bca-s8-gis-016": "Define DEM and DTM, distinguish them, and explain their applications in terrain analysis.",
"question-bca-s5-mise-009": "Explain the e-commerce framework and the role of the Internet and Web in e-commerce growth.",
"question-bca-s7-database-administration-018": "Define backup, restore and recovery in Oracle. Describe an organizational backup/recovery strategy, recovery for different failure types, and the media-recovery process.",
"question-bca-s6-mp-015": "Explain AbsoluteLayout’s positioning attributes. Develop an Android calculator using AbsoluteLayout with two numeric inputs, separate multiplication and division buttons, and a TextView displaying the result.",
"question-bca-s6-mp-018": "Differentiate activities and fragments. Develop an Android app whose first fragment accepts a rectangular box’s length, width and height; a Volume button passes them to a second fragment, which calculates and displays the volume in a TextView.",
}


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def build():
    catalog = json.loads((STAGE1 / "curriculum.json").read_text())
    files = {"curriculum.json": (STAGE1 / "curriculum.json").read_bytes()}
    seen = set()
    for entry in catalog["subjectFiles"]:
        doc = json.loads((STAGE1 / entry["path"]).read_text())
        for q in doc["questions"]:
            seen.add(q["id"])
            if q["id"] in OVERRIDES:
                q["question"] = OVERRIDES[q["id"]]
            if q["id"] in EXCLUDED:
                q["isActive"] = False
        files[entry["path"]] = encoded(doc)
    assert set(OVERRIDES) | EXCLUDED <= seen
    rows = [dict(path=p, sha256=hashlib.sha256(b).hexdigest()) for p, b in sorted(files.items())]
    digest = hashlib.sha256("".join(f"{r['path']}\t{r['sha256']}\n" for r in rows).encode()).hexdigest()
    files["bundle-manifest.json"] = encoded(dict(datasetId="bca-stage1-reviewed", contentVersion=1, schemaVersion=1, bundleDigest=digest, files=rows))
    return files


def main():
    files = build()
    # Preflight every existing output before writing any new file.
    for path, data in files.items():
        target = OUTPUT / path
        if target.exists() and target.read_bytes() != data:
            raise ValueError(f"Conflicting frozen output: {path}")
    for path, data in files.items():
        target = OUTPUT / path
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            with target.open("xb") as out:
                out.write(data)
    print("Reviewed Stage1:434 records,427 active,19 prompt edits,6 exclusions;12 bundle files")


if __name__ == "__main__":
    main()
