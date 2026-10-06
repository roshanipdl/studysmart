# Schema-v2 formatted answers — device deployment pending approval

**2026-10-06 implementation update:** the latest approved implementation plan
authorizes debug packaging and local build verification of this frozen bundle.
Android now supports explicit schema-1/schema-2 import, Room v8 format snapshots,
restricted-HTML validation and native answer rendering. Device deployment and
instrumentation still require the parent session's separate scoped approval;
production/release approval is not implied. Implementation evidence is recorded
in `implementation/formatted-stage-one-deployment.md`.
**Independent step-6 review is incomplete.** A reviewer scanned 434 answers, but
long-answer previews were truncated: full independent editorial inspection of
434 recipes and 120 blocks is not established. The reviewer ran no tests. Local formatting
review is not factual/editorial approval or redistribution permission.

The historical conversion evidence below describes the earlier data-only task.
Only this excluded report is updated by the implementation task; the manifest
and all 11 curriculum data files remain frozen. Builds and grammar tests do not
complete independent editorial signoff.

## 1. Scope and immutable evidence

Batch: `stage1-format-v2`; source: `database-stage-1-reviewed` only. All 434
answers, including seven inactive records, were reviewed and formatted. No raw
`data/` or Stage 2 files were read. DBMS raw extraction remains deferred; its
already-reviewed JSON answers are included without inspecting raw sources.
Original Stage 1, reviewed Stage 1 and guide/ledgers are unchanged. This task made
no Android writes and preserved unrelated outputs. `baseline.json` captures hashes
and the pre-existing Android Git status/diff fingerprints.

- Source bundle digest: `a7b1393d92dbc83f8817671ec5758efe7308f9b4db8929353757f72e916a57f6`.
- Source manifest SHA-256: `9d4d4a643e7f5e9d6d1cfebc76f4ed37e5a2fb1256d5947d91a3cdbeaef96f4a`.
- Source review report SHA-256: `c10afce7443aa4f9516d0e3c83e5f3123012c0877cf3774a64ef09b4334a2bd1`.
- Recipe SHA-256: `1c9304d55f18690140d3b3daba9bc3235d8390598a30815ca5fed4b00f28d5b4`.
- Frozen proposal bundle digest: `876b3154fca24caa8324c3613d2905da1336924f5c9ec1c5b7576f2d7fede6e5`.
- Fixed question `updatedAt`: `1791244800000` (2026-10-06T00:00:00Z), at least
  the latest source question update `1791048986958`. Every `createdAt` is preserved.

Verified exact source-file SHA-256 values:

| Relative source path | SHA-256 |
|---|---|
| `curriculum.json` | `bd228dc8a2a0e133545497071c853b13e416896b404961be450d3fb4737ce443` |
| `subjects/subject-bca-s4-dbms.json` | `742ef7f99e0d6cbd646f9c6cb45d8c95c9e24140631282fc8e9d3e0f11a3dac5` |
| `subjects/subject-bca-s5-dnt.json` | `e7e65ee088e0d096458763d5b16304a53f0dafdc3433eb84b14c875843def15c` |
| `subjects/subject-bca-s5-mise.json` | `c97d722ecf2d350c9b15a08cb6e86c1faddc4c3da55fbe920bd6ede565a1f235` |
| `subjects/subject-bca-s6-dcc.json` | `8bb493fd665fb53f55ced73a4ea29d1d48a24d2c176ab2d373cfdbb54ec2a697` |
| `subjects/subject-bca-s6-mp.json` | `9ee357b65e5ed191d8c54caf8f21d07423d9f274f97536f0857130c9734bfc3f` |
| `subjects/subject-bca-s7-database-administration.json` | `461a618a086a5eac797ec2305d773dd77c746d5e14317e5be1dcce19ca3863d5` |
| `subjects/subject-bca-s7-na.json` | `eb9eb23b357bf567412f6c7129b2d6a77ba2da4316fbf44b29f8714fa0cd6b01` |
| `subjects/subject-bca-s7-spm.json` | `0c1a32a0a67051838559ca0af2452f62175d0e13cc4c44b00093b63cc90ae781` |
| `subjects/subject-bca-s8-gis.json` | `ad1a6c248ec519dbf17f37140a42370084e9f46738cf680fcc4143458cddefb6` |
| `subjects/subject-bca-s8-ms.json` | `221b2d081d80d945a8ec75825184dc7a66bf75a2e942a805aa162c1b8d8308c7` |

## 2. Per-subject results

Formatting coverage is complete for this immutable snapshot, not for the raw
curriculum. Structural/delta verification passes with the inherited ordering
warning below. Independent formatting and human editorial review remain pending.

| Subject | Chapters | Topics | Questions | Active | Keywords | Questions without keywords |
|---|---:|---:|---:|---:|---:|---:|
| S4 DBMS | 9 | 0 | 35 | 30 | 77 | 6 |
| S5 DNT | 4 | 0 | 41 | 41 | 76 | 8 |
| S5 MISE | 6 | 0 | 56 | 56 | 105 | 13 |
| S6 DCC | 9 | 0 | 67 | 67 | 181 | 5 |
| S6 Mobile Programming | 8 | 0 | 54 | 53 | 66 | 21 |
| S7 Database Administration | 8 | 0 | 40 | 40 | 99 | 3 |
| S7 Network Administration | 5 | 0 | 29 | 29 | 75 | 2 |
| S7 SPM | 7 | 0 | 63 | 62 | 189 | 0 |
| S8 GIS | 5 | 0 | 31 | 31 | 62 | 4 |
| S8 Multimedia | 8 | 0 | 18 | 18 | 42 | 3 |
| **Total** | **69** | **0** | **434** | **427** | **972** | **65** |

One course, five academic periods and ten subjects remain unchanged. The dataset
contains 519 entity definitions plus 972 unchanged keyword rows. All 434 answers
declare `answerFormat: "html"`; plain-text answer count is zero.

## 3. Formatting review, issues and exclusions

Every recipe contains an answer hash, Unicode-codepoint length, explicit source
slices, complete ordered/nonoverlapping coverage and review notes. Runtime
generation uses the frozen recipe, never heuristic formatting. The optional
authoring aid's reviewed code boundaries are not used by the publisher.

All text is HTML-escaped once using `html.escape(..., quote=False)`, then separately
JSON-encoded. Only whitespace separators and explicitly identified source list/
Markdown syntax markers are consumed without visible text. Decoded visible text
equals the retained source slices exactly. No new prose, language labels,
headings, missing code or explanation content is invented. Source emphasis maps
to inline `em`/`strong`; balanced backticks map to inline `code`. Existing short
headings receive selective emphasis. Continuous source numbering 1..N maps to
`ol`; interrupted/nonstandard numbering stays literal in labeled `ul` items.
Nested list indentation is represented structurally. SPM single-newline sections
are distinct paragraphs, with comparisons represented as existing labeled lists.

There are 1,012 paragraphs, 194 unordered lists, 153 ordered lists, 1,556 list
items, 132 strong elements, 91 emphasis elements, 122 inline-code elements and
120 exact-whitespace code/notation/diagram blocks. Eleven whole-answer code blocks
are warranted: Mobile Programming 014, 019, 023, 027, 030, 031, 033, 036, 040,
044 and Oracle 045 consist entirely of instructional code/comment transcripts.
There is no whole-answer-pre fallback for ordinary prose.

Critical local checks:

- `question-bca-s5-dnt-016`: the complete jagged-array C# program is a code block;
  matrix multiplication remains the supplied prose. No absent matrix program is
  reconstructed. `<` in the loop is escaped and decoded back exactly.
- `question-bca-s7-database-administration-042`: retain all merged roman-numbered
  instructions literally; the SQL/OS command transcript is a separate block.
  Do not invent missing breaks or claim the transcript is one executable script.
- `question-bca-s7-database-administration-045`: the complete commented SQL and
  embedded `expdp` instruction are code. Preserve all command indentation,
  comments, strings and blank lines, including the OS-command context.
- `question-bca-s4-dbms-013`: preserve σ, π, ∧, quoted names and the isolated
  RA expression exactly; do not rewrite it into SQL.
- `question-bca-s8-ms-010`: the source says "For the board string" but provides
  no literal string. Preserve this inherited gap; do not invent one from the runs.

**Inherited ordering warning:** Oracle question arrays within
`chapter-bca-s7-database-administration-01`, `-02` and `-03` are not sorted by
`sortOrder`. An initial strict structural check failed on that source order.
The approved preservation requirement takes precedence over sorting changes in
this formatting-only batch. This exception is explicit and limited to those
source groups; exact-byte regeneration rejects every array-order delta. All
scoped sort orders remain unique and unchanged. Canonical sorting is therefore
not claimed for this snapshot.

Inherited incomplete exercises, factual/terminology uncertainties, cross-card
references, sample connection credentials/password literals and dated legal
claims remain source text, not endorsed operational instructions. No code was
compiled or executed. Demonstration passwords/addresses are not new credentials;
review their redistribution/security teaching quality before release. All seven
inactive flags remain unchanged. Unsupported missing media or missing evidence is
not repaired by formatting. Copyright/licensing and human editorial approval are
not established by this task.

Source-characteristic checks found 374 answers containing newlines, 205 with
line-start list markers (`^\s*(?:[-*] |\d+\. )`), and zero fenced-code answers.
77 answers contain literal `<` or `>` (including comparisons/arrows), and 24 match
`<\/?[A-Za-z]`. The plan's prior "31 angle/markup" classification was not
reproduced with those predicates; no content decision depends on that count.
All reserved characters in all 434 answers were checked, not just flagged samples.

Reviewer correction: explicit source spans `[1013,1166)` in NA-013 and `[0,310)`
in NA-028 now render as `em`, preserving interior text and newlines. All 434
source answers were scanned for balanced multiline/long asterisk candidates.
Other candidates were SQL wildcards, multiplication operators, or cross-marker
matches; these remain literal, not automatically interpreted as emphasis.
The prior recipe is archived as `formatting-recipe.pre-review.json`. Explicit
regeneration checked every prior proposal hash against recorded evidence before
changing only the NA subject and manifest; no source/original bundle was deleted.

Formatting failures by question ID after local verification: **none**.

## 4. Automated validation evidence

Executed from `/workspace` using existing Python standard library only:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 '/workspace/Android data/.conversion-staging/stage1-format-v2/tools/author_recipes.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s '/workspace/Android data/.conversion-staging/stage1-format-v2/tools' -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python3 '/workspace/Android data/.conversion-staging/stage1-format-v2/tools/format_stage_one.py' --source '/workspace/Android data/database-stage-1-reviewed' --recipe '/workspace/Android data/.conversion-staging/stage1-format-v2/formatting-recipe.json' --output '/workspace/Android data/database-stage-1-formatted'
PYTHONDONTWRITEBYTECODE=1 python3 '/workspace/Android data/.conversion-staging/stage1-format-v2/tools/validate_formatted_answers.py' --source '/workspace/Android data/database-stage-1-reviewed' --recipe '/workspace/Android data/.conversion-staging/stage1-format-v2/formatting-recipe.json' --output '/workspace/Android data/database-stage-1-formatted'
PYTHONDONTWRITEBYTECODE=1 python3 '/workspace/Android data/.conversion-staging/stage1-format-v2/tools/verify_workspace.py'
```

- Authoring verification: all 434 recipes passed source coverage, text escaping
  and exact code-whitespace checks. All source answers and recipe structures
  were locally inspected; this is author review, not independent signoff.
- Unit tests: **35 passed**, no failures or skips in the final local run, including
  multiline/long emphasis regression coverage. Earlier
  strict-order check failure is disclosed above and resolved by a preservation
  exception, not by modifying source data.
- Prevalidation occurs on all final JSON bytes before destination creation.
  Manifest verification covers exactly 11 sorted data paths using
  `path + TAB + lowercase SHA-256 + LF`; the manifest excludes itself, report
  and staging tools. Actual proposal bytes are revalidated against pinned source
  and recipe. UTF-8, two-space indentation and final LF are deterministic.
- Explicit parser stack enforces lowercase permitted tags, closed/nested grammar,
  permitted text contexts, meaningful visible content, lists containing `li`,
  and `pre` containing exactly one text-only `code`. Attributes, comments,
  declarations, processing instructions, self-closing tags, malformed tags,
  numeric/unsupported/unterminated entities and unescaped reserved text fail.
- JSON duplicate keys, NaN/Infinity, float overflow, malformed recipes, source
  hash changes, missing/extra IDs, bounds/coverage gaps/overlaps, invalid paths,
  symlink aliases/escapes, unexpected field/type/relationship/numeric/provenance/
  keyword/active/order changes, invalid formats, double escaping and modified code
  whitespace are rejected by grammar/contract/frozen-delta tests.
- Same bytes rerun is a no-op: all 12 bundle-file hashes and nanosecond mtimes
  were independently compared before/after generation. Conflicting outputs are
  rejected without overwrite. Simulated interruption retains a recognizable
  `generation-incomplete.json`; ingestion/validation refuses that output.
- Shared Android baseline drift was detected: the first workspace verification
  failed its Git-status assertion because unrelated shared-workspace status had
  substantially changed. No Android edits were made by this task and no external
  work was reverted. Workspace verification reports that drift instead of
  claiming global Android preservation. Source/Stage 1/ledger hash checks passed.
  The actual final workspace evidence is retained in
  `.conversion-staging/stage1-format-v2/verification-results.json`; all 13 proposal
  files including this report retained their bytes and mtimes during that rerun.
- Publication marker is checked before generation and at completion. No
  production publication or ledger update was performed; this is a new proposal
  directory, not replacement of a published dataset.

**Not run / not established:** raw provenance-locator/file-existence rechecks
(raw reads prohibited); current Android keyword-normalization parity (keywords
unchanged); source factual correctness; source-code compilation; browser/device
rendering, accessibility or layout verification; Android builds/tests/import;
independent reviewer. No existing local visual-preview tool was found under
`Android data/`; visual preview was not run, and no browser environment or tooling
was modified. Android compatibility was inspected as text only:
`CurriculumJsonParser.kt` rejects schema 2; `PreparationScreens.kt` passes
`item.answerSnapshot` directly to Compose `Text`.

## 5. Change summary

Created only the new staging batch and `database-stage-1-formatted/` proposal.
All 11 envelope revisions advance from 1 to 2; catalog and subject envelopes and
manifest declare schema 2. Manifest dataset ID is `bca-stage1-formatted`, content
version 1. Formatting changes all 434 answers, adds their declared format and
uses the one fixed question update timestamp. No questions are added, deleted,
retired or reactivated. Every other entity field, array order, ID, keyword set,
provenance entry, hierarchy and creation timestamp is identical to reviewed
Stage 1. The report is deliberately absent from both catalog and manifest.

## 6. Handoff status

**Locally verified data-only formatting proposal; NOT deployable.** Limited reviewer
feedback was addressed; full independent review has not passed. Parent session
must perform step 6: read-only review of all 434 source/recipe/formatted answers,
the 120 code/notation/diagram blocks, safety/coverage tests and preservation
evidence; resolve findings and rerun checks before final acceptance.

Future app support requires explicit schema/format parsing, format persistence,
safe restricted rendering, decoded search text, preparation/test snapshot format
propagation, backwards-compatible plain-text handling and separate integration
verification. Existing learning history and snapshots must not be rewritten.
65 keywordless questions retain their grading limitations. No app readiness,
grading improvement, complete-source coverage or human editorial approval is
claimed by this proposal.
