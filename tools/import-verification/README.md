# Curriculum import JVM verification

Run from `android/`:

```text
./gradlew -p tools/import-verification test --max-workers=1
```

This SDK-independent harness compiles the actual import parser, validator,
engine, Room entity/DAO declarations and model sources. It runs the same importer
JVM tests against the frozen Stage 1 snapshot. It uses a JVM org.json dependency;
platform-parser and real Room transaction/migration behavior require Android
instrumentation separately. This is not an APK, device or UI verification.

Normal Android JVM checks also support these tests through the app's test-only
org.json dependency and `curriculum.snapshot` system property. Asset preparation
is `:app:prepareDebugCurriculumAssets`; release asset checks must confirm the draft
bundle is absent. Do not install or clear app packages without required approval.

All three fixture properties resolve to `../../curriculum/database-stage-1*`
inside this repository; no sibling project data is needed. The `../../app`
source references are internal to this repository. SDKs, Java and Gradle caches
remain normal external tooling, not curriculum source dependencies.
Both app JVM tasks and this harness declare all three fixture directories as
test inputs so fixture edits invalidate Gradle's up-to-date test results.

`:app:validateDebugCurriculumBundle` checks the approved formatted bytes even
when Sync has no sources. `:app:verifyDebugCurriculumApk` assembles debug and
checks the current output metadata, exact 12-entry asset inventory, manifest
bytes and all 11 manifest hashes. Neither task packages release curriculum.
