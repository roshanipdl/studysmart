# Android Workspace Notes

## Verification Boundary

- This container does not have a usable `aapt2`: the shared CLI runner is
  `aarch64`, while the installed Android SDK exposes an x86-64 `aapt2`.
  Android resource processing and APK builds are therefore unavailable here.
- Android SDK level checks, including `compileSdk`, `targetSdk`, `minSdk`,
  installed platform APIs, and build-tools compatibility, must be performed on
  the host Android toolchain.
- Do not download, install, or modify Android SDK platforms, build-tools, or
  command-line tools in this container; use the host toolchain for those checks.
- APK installation, emulator/device testing, and Compose UI testing normally
  remain manual host checks. The developer may explicitly approve an agent-run
  `:app:connectedDebugAndroidTest` invocation under the exception below.
  Release-build verification remains manual on a host Android toolchain.
- Agents should not spend time emulating x86 Android build tools here.
- Agent-side verification should otherwise stay lightweight: Gradle configuration,
  dependency metadata, KSP/source checks when available, basic linting, and
  targeted JVM tests that do not require device execution.

## Explicitly Approved Connected Debug Tests

- Run `:app:connectedDebugAndroidTest` only after the developer explicitly
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
