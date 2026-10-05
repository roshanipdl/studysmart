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
