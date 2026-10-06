# Stage 1 import verification — 2026-10-04

## Portability verification — 2026-10-06

### Minor review follow-up — inventory attributes and test inputs

Added exact `-text` rules for the three inventoried provenance Markdown files;
no blanket Markdown/README policy. Worktree `git check-attr -z --stdin text`
against all 39 `SHA256SUMS` paths reports `text: unset` for every file, without
staging. All 39 hashes still match. `curriculum/README.md` and the harness README
report `text: unspecified`. App Test tasks and the harness now declare the three
fixture directories as named inputs for correct up-to-date invalidation.

Commands from `android/`, existing SDK environment, one worker:

```text
./gradlew -p tools/import-verification --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' test
./gradlew --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' :app:testDebugUnitTest --tests 'com.studysmart.app.core.data.importer.*' :app:lintDebug :app:assembleDebug :app:verifyDebugCurriculumApk :app:compileDebugAndroidTestKotlin
```

Both commands exited 0. Test tasks executed: harness 41 tests and app importer
20 tests, zero failures/errors/skips in their XML reports. Lint passed; debug
assembly and Android-test compilation were up-to-date (not device execution).
APK verification executed and confirmed the exact 12-entry inventory and
approved bundle digest. Reports remain in the paths documented below. Existing
local.properties SDK warning persists; no SDK changes, devices or commits.

Sequential todos 1–6 completed. The initial disposable-directory blocker was
resolved using the subsequently approved in-workspace location below.
Historical evidence below is unchanged.

All commands ran from `android/` with these bounded options:
`--no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'`.

- `./gradlew -p tools/import-verification <options> test`: passed, 41 tests,
  zero failures/errors/skips.
- `./gradlew <options> :app:prepareDebugCurriculumAssets`: passed; independent
  validation executed before Sync.
- `./gradlew <options> :app:verifyDebugCurriculumApk`: passed.
- `./gradlew <options> :app:testDebugUnitTest --tests
  'com.studysmart.app.core.data.importer.*' :app:lintDebug :app:assembleDebug
  :app:verifyDebugCurriculumApk :app:compileDebugAndroidTestKotlin`: passed,
  20 importer tests, zero failures/errors/skips. Compilation is not execution
  of instrumentation. Existing ReviewedStageOneStartupTest retained unchanged.

Reports: `tools/import-verification/build/test-results/test/`,
`app/build/test-results/testDebugUnitTest/`, and
`app/build/reports/lint-results-debug.html`.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`9dde2b0c9553ba97df654ce6201fd2ab6cb0f3681bee86ea788cc9abc3bc0dff`.
ZIP verification proved exact 12-entry curriculum inventory, exact source
manifest bytes, and all 11 data hashes; approved bundle digest:
`876b3154fca24caa8324c3613d2905da1336924f5c9ec1c5b7576f2d7fede6e5`.
All 39 copied provenance/data files were byte-compared against the originals;
their hashes are in `curriculum/SHA256SUMS`. No originals were removed.

Scoped active build/test audit found no external curriculum source dependencies.
Optional `tools/curriculum-data` Stage 2 authoring scripts still refer to sibling
authoring data; they are not invoked by builds/tests and were left unchanged.
Historical report paths were not rewritten. Release source-set wiring unchanged.

Environment: existing Java/Gradle and `/opt/android-sdk` via ANDROID_HOME and
ANDROID_SDK_ROOT. No SDK paths or tooling were changed. Existing local.properties
host-path warning persists. Many Android outputs were up-to-date: this is not
fresh source-only build evidence.

**Initial blocker (superseded):** creating a source-only disposable under `/tmp/opencode`
failed with PermissionError. `ls -ld /tmp/opencode` confirmed root ownership
and mode 0755; no permissions were changed. Source-only harness/build/APK checks
and disposable-only withheld-bundle failure check remain unrun. No device,
installation, emulator, instrumentation, release build, commit or staging run.

### Completed source-only isolation verification

Approved replacement run directory (relative to `android/`):
`build/standalone-verification/884fa8b504214b2ea78e7a983b839fad/`.
The isolated project is its `android/` child. Copied 372 tracked/intended-new
source files, byte-compared each file, and explicitly compared root Gradle files,
wrapper, app configuration and harness configuration. The source inventory and
per-file SHA-256 values are in `source-inventory.json` in the run directory.
Selection was `git ls-files` plus `.gitattributes` and all intended `curriculum/`
files, not a recursive workspace copy. No `.git`, build outputs, project caches,
local.properties, credentials, signing files or logs were copied; no sibling
`Android data` exists at the isolated root. Shared standard Gradle dependency
caches and existing SDK environment remained available; no SDK changes.

From that isolated `android/` directory, exact commands:

```text
./gradlew -p tools/import-verification --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' test
./gradlew --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' :app:assembleDebug :app:verifyDebugCurriculumApk
./gradlew --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' :app:prepareDebugCurriculumAssets
```

- Harness: exit 0, 41 executed tests, zero failures/errors/skips; all four
  actionable tasks executed. Reports:
  `android/tools/import-verification/build/test-results/test/` inside the run.
- Fresh debug assembly and APK validation: exit 0, all 41 actionable tasks
  executed, including resource processing, Kotlin/KSP, asset generation and
  packaging. No preexisting generated assets were copied. Existing Room index
  and native-library stripping warnings did not fail the build.
- Isolated artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
  `7a3753bba0a64208694bc0b411c1d0ec0f8bf8df4bd94acf94f18db86f2890d2`.
  Verified exact 12-entry curriculum inventory, all 11 manifest hashes, approved
  bundle digest above and exact manifest bytes. Manifest SHA-256:
  `cd80f67d28c6f2aa70ba5d0fad35ff0f0afecb4f7bc7b65ea1e170483dbad2ac`.
- Negative check: renamed only the disposable formatted snapshot to the run
  directory, leaving generated assets present. The third command exited 1 at
  `:app:validateDebugCurriculumBundle FAILED` with
  `Required curriculum bundle missing: .../curriculum/database-stage-1-formatted
  (restore curriculum/database-stage-1-formatted)`. Sync never ran: stale outputs
  cannot turn absent source into success. Restored the disposable bundle by
  rename in a finally block; no original data was moved or deleted.

Full run logs: `harness.log`, `android-build.log`, `missing-bundle.log`; negative
exit evidence: `missing-bundle-exit.txt`, all in the run directory. Artifacts are
left in the generated directory. No devices, instrumentation, installation,
release checks, broad tests, clean tasks, permission changes or commits ran.

## Passed in the container

From `/workspace/android`:

```text
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug -Pandroid.injected.sdk.dir=/opt/android-sdk --console=plain --max-workers=1 --no-daemon -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process
```

- Android JVM suite: 228 tests, 0 failures, 0 errors, 0 skipped across 40 classes.
- Instrumentation Kotlin/KSP compilation passed; no instrumentation was executed.
- Debug lint: 0 errors/fatal issues, 24 warnings.
- Reports: `app/build/test-results/testDebugUnitTest/`,
  `app/build/reports/tests/testDebugUnitTest/`,
  `app/build/reports/lint-results-debug.html` and `.xml`.
- Android resource tasks reused existing outputs. This is not proof that this
  container can freshly rebuild resources or produce a verified APK.
- Local SDK paths were not edited. The command used an invocation-scoped SDK
  property; the checked-in/local host SDK path warning remains expected here.

```text
./gradlew -p tools/import-verification test --console=plain --max-workers=1 --no-daemon -Dorg.gradle.jvmargs='-Xmx512m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process
```

- 15 SDK-independent importer tests pass. Reports are under
  `tools/import-verification/build/test-results/test/` and `build/reports/tests/test/`.
- Includes actual Stage 1 parsing/storage normalization, hash corruption, unsafe
  paths, invalid IDs/ancestry/scoring, idempotency, collisions, version mismatch,
  content updates, omitted descendant protection, strict JSON and schema overflow.

```text
./gradlew :app:prepareDebugCurriculumAssets :app:mergeReleaseAssets --console=plain --max-workers=1 -Pandroid.injected.sdk.dir=/opt/android-sdk
```

- Generated debug assets were independently byte-compared to Stage 1; only the
  manifest, catalog and ten subject files are present.
- Merged release assets contained no catalog or bundle manifest.
- Room KSP generated schema 6. A SQLite DDL check verified that migration 5→6
  preserves existing table definitions and an existing marker row, and that the
  generated Room schema adds only `curriculum_import_state`.
- Snapshot freeze, structural validation, repeated byte/hash checks and
  `git diff --check` passed.

## Failures diagnosed and corrected

- Initial parsing assumed provenance notes were required; the schema makes them
  optional. Corrected and reran the tests.
- Gradle worker/daemon failures occurred under memory pressure (OOM counter
  increased). Reduced heap/workers, used in-process Kotlin compilation and ended
  only this run's idle compiler/build processes. Subsequent checks passed.
- Combined unit/lint execution found missing generated-asset task dependencies.
  Added explicit debug lint dependencies and repeated the combined command.
- Reviewer found merged-ancestry, permissive JSON and schema-narrowing issues.
  Added guards and regressions; reviewer recheck reported no remaining findings.

## Not run / required host acceptance

- No APK assembly, installation, app launch or physical-device import was claimed.
- No connected test approval was provided. `CurriculumImportTest` (platform JSON,
  fresh seeded import, repeat import, bookmark preservation, failed/cancelled
  transaction rollback) and the supported migration matrix 1–5→6 must run on an
  approved selected device through a working host Android build route.
- Debug/release share `com.studysmart.app`; review installed package compatibility
  and data risk before any update. Do not clear app data or uninstall.
- On the host, build debug, verify the final APK asset inventory, update a
  compatible installation without data reset, launch offline, browse the imported
  content, restart, and verify existing learning data is preserved. Release APK
  draft-asset exclusion also remains a host acceptance check.
- Stage 1 remains partial draft material; release inclusion and redistribution
  are not approved by these technical checks.
