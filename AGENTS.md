# Android Workspace Notes

## Verification Boundary

- The shared CLI runner is `aarch64`, and the inspected SDK `aapt2` at
  `/opt/android-sdk/build-tools/35.0.0/aapt2` is x86-64. This is a compatibility
  concern, not proof that the current Gradle build is unavailable: prior container
  debug app/test APK builds succeeded using the existing SDK environment.
  Inspect existing tooling, Gradle overrides and task outputs, then attempt the
  scoped build before declaring it blocked. Diagnose the actual failure; do not
  assume that SDK installation is necessary or that TCP ADB provides build tooling.
- Android SDK level checks, including `compileSdk`, `targetSdk`, `minSdk`,
  installed platform APIs, and build-tools compatibility, must be performed on
  the host Android toolchain.
- Do not download, install, or modify Android SDK platforms, build-tools, or
  command-line tools in this container; use the host toolchain for those checks.
- APK installation, emulator/device testing, and Compose UI testing normally
  remain manual host checks. The developer may explicitly approve an agent-run
  `:app:connectedDebugAndroidTest` or direct-ADB instrumentation invocation under
  the exception below. Reuse the documented container/host-ADB route when viable.
  Release-build verification remains manual on a host Android toolchain.
- Agents should not spend time emulating x86 Android build tools here.
- Agent-side verification should otherwise stay lightweight: Gradle configuration,
  dependency metadata, KSP/source checks when available, basic linting, and
  targeted JVM tests that do not require device execution.

## Explicitly Approved Connected Debug Tests

- Run `:app:connectedDebugAndroidTest` or direct-ADB instrumentation only after the developer explicitly
  approves the current run. Approval must establish the test scope; prefer
  focused classes for the current change. Do not treat one approval as standing
  permission for later runs, retries, or the entire test suite.
- Before execution, load and follow the `android-container-verification` skill.
  Confirm the exact debug application ID, test application ID, instrumentation
  runner, target package, and selected device serial. Review test setup/teardown
  and task dependencies for side effects, including data clearing.
- Use existing container tooling or a documented host execution route. Cached
  resource outputs may permit a run, but ADB connectivity or successful source
  compilation alone does not establish that current APKs can be built. Verify
  that the deployed APKs contain the current source; never substitute stale APKs.
- Target only the selected device and this project's confirmed app/test packages.
  For Gradle connected tests, verify that its device-selection mechanism honors
  the selected serial; block execution if it could affect another device.
- Compatible, non-destructive app/test APK updates required by the approved run
  are allowed after checking installed package identity and compatibility. Shared
  production/debug application IDs require a data-collision risk review. Approval
  to test does not authorize uninstalling packages, clearing app data, replacing
  installations of uncertain compatibility, changing permissions, or tests that
  erase existing user data; obtain separate action/package/device-specific approval.
- This exception does not permit SDK/tooling installation, x86 emulation, emulator
  provisioning, global device changes, or operations on unrelated apps. Preserve
  all global agent safety rules. If the build route, device scope, test effects,
  or installation compatibility cannot be established, stop and report the blocker.
- Report the exact command, device/package targets, actual executed-test counts,
  failures/skips, and report paths. Compilation, cached reports, successful APK
  installation, or an empty/skipped run must not be reported as passing tests.

## Previously Successful Container / TCP-ADB Route

- Evidence: `/workspace/implementation/mvp.md` records container Linux aarch64,
  Gradle 8.13 and Java 21 debug app/test builds, followed by **90** and **99**
  successful direct-ADB instrumentation tests on ONEPLUS A6013. These are historical
  results, not proof that a current build or test run will pass.
- A successful bounded-memory build command, run from `/workspace/android`, was:

  ```text
  ./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
  ```

- Existing environment uses `/opt/android-sdk` and the host ADB server through
  `ADB_SERVER_SOCKET=tcp:host.docker.internal:5037`. The host-only path in
  `local.properties` emitted a warning during prior successful builds; do not
  rewrite SDK paths unnecessarily. Recheck the current environment before use.
- Enumerate devices with `adb devices -l`; select and validate the current serial
  rather than blindly reusing the historical wireless serial. Confirm app/test
  package identity, signing/update compatibility and test side effects first.
- With current-run approval and verified current APKs, prior deployment used
  `adb -s <confirmed-serial> install -r <verified-apk>` for each project package,
  without uninstalling or clearing app data. Verify the APKs contain the current
  source and intended curriculum assets; cached outputs alone are insufficient.
- Gradle connected testing previously timed out connecting to ADB. With approval
  for the focused direct-instrumentation scope, the working alternative was:

  ```text
  adb -s <confirmed-serial> shell am instrument -w -r -e class <approved-test-class> com.studysmart.app.test/androidx.test.runner.AndroidJUnitRunner
  ```

  The package/runner above is historical and must be revalidated for the selected
  variant. Report direct instrumentation separately from Gradle connected tests;
  never call a timed-out connected task passed. Historical approvals do not carry
  forward, and a fallback or retry needs current-run approval for its scope.
- This route does not relax SDK installation, emulation, device safety or
  destructive-action restrictions. If the current build fails, report the exact
  command/error and diagnose existing tooling before requesting a host build.

## Host Verification

Run these from `/workspace/android` on a host with a working Android SDK:

```text
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Manual acceptance remains required for app launch, navigation, Room
initialization, backup behavior, accessibility, and device behavior.

## Latest Developer Verification

- The developer verified the app on a physical Android device.
- Home, Courses, Review, Progress, and Important navigation was verified through
  the bottom navigation.
- Host `testDebugUnitTest` and `lintDebug` verification was reported complete
  after the route-encoding fix.
