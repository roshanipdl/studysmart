# Stage 1 import verification — 2026-10-04

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
