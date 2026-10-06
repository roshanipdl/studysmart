# Stage 1 review — 2026-10-06

Read-only reviewer inspected all 433 active prompts across ten subjects; one
additional prompt is already inactive. Original Stage 1 remains unchanged.

Review identified 19 warranted prompt corrections and six items unsuitable for
the approved active set without additional evidence. No new sources were used.

## Exclude from active study pending repair

- `question-bca-s4-dbms-012`: required input relations missing.
- `question-bca-s4-dbms-014`: complete schemas and five tasks missing.
- `question-bca-s4-dbms-018`: normalization table/dependencies missing.
- `question-bca-s4-dbms-023`: several unspecified exercise sets.
- `question-bca-s4-dbms-035`: missing concrete ER scenario; mismatched chapter.
- `question-bca-s7-spm-046`: reference answer concerns financial, not software-project, risk.

Preserve these records as inactive; do not delete original source records.

## Prompt corrections required

Restore requirements or terminology from existing answers:
`question-bca-s4-dbms-013`, `question-bca-s4-dbms-015`,
`question-bca-s7-database-administration-030`,
`question-bca-s7-database-administration-042`,
`question-bca-s7-database-administration-045`, `question-bca-s5-dnt-016`,
`question-bca-s5-dnt-021`, `question-bca-s7-na-019`, `question-bca-s8-ms-004`,
`question-bca-s5-mise-036`, `question-bca-s5-dnt-002`,
`question-bca-s5-dnt-024`, `question-bca-s5-dnt-028`, `question-bca-s8-gis-016`.

Remove extraction noise or redundant wording:
`question-bca-s4-dbms-002`, `question-bca-s5-mise-009`,
`question-bca-s7-database-administration-018`, `question-bca-s6-mp-015`,
`question-bca-s6-mp-018`.

The 40-word target is editorial, not automatic truncation. Restored Oracle
command sets require longer prompts to preserve tasks. No chapter reassignment
or legal/regulatory answer updates are authorized by this prompt review.

## Status

Source prompt review, immutable bundle generation, debug packaging and runtime
seed isolation are implemented. The Python source-derivation/hash test passed;
332 Android JVM tests passed with no failures/errors/skips. Lint and debug
app/test APK assembly passed. The final APK contains exactly the 12 reviewed
bundle files, byte-identical to this directory. Implementation reviewer found
no issues. With explicit package/device approval, the app was uninstalled to
resolve a signing-key mismatch and remove old local data, then the reviewed APK
was installed on ONEPLUS A6013. Cold launch succeeded. The test package was
separately uninstalled/reinstalled with approval for the same signing mismatch.
Six focused direct-ADB import/startup tests passed with no failures or skips.
Manual question browsing, preparation/mock-test journeys and restart persistence
acceptance remain pending; these are not established by in-memory import tests.
Answer completeness/current legal correctness is not established by this review.

Dataset: `bca-stage1-reviewed`, content/schema version 1; 434 question records,
427 active. Original answers, keywords, IDs and hierarchy are preserved. Three
Oracle task prompts exceed the editorial length target because restoring missing
requirements is preferable to silently discarding tasks. No generated filler.

## Verification commands and evidence

From `android/tools/curriculum-data`: `python3 -m unittest -v test_stage_one_reviewed`.

From `android`:

```text
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

Reports: `android/app/build/test-results/testDebugUnitTest/` and
`android/app/build/reports/lint-results-debug.html`.
Build initially failed on an existing compressed manifest archive; removal was
limited to generated reviewed-bundle archives and the subsequent build passed.
Resources reused incremental outputs; this is not a clean-resource-build claim.
Stage 2 cleanup is disconnected. Release seeding remains unchanged.

Device evidence: `android/app/build/reviewed-stage-one-device-results.txt`.
Selected serial: `adb-023e26f9-haWIZa._adb-tls-connect._tcp`.

```text
adb -s adb-023e26f9-haWIZa._adb-tls-connect._tcp shell am instrument -w -r -e class com.studysmart.app.core.database.CurriculumImportTest,com.studysmart.app.core.database.ReviewedStageOneStartupTest com.studysmart.app.test/androidx.test.runner.AndroidJUnitRunner
```

Result: `OK (6 tests)`. Both project packages remain installed.
