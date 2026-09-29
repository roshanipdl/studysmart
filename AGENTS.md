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
- APK installation, emulator/device testing, Compose UI testing, and
  release-build verification will be performed manually by the developer on a
  host Android toolchain.
- Agents should not spend time emulating x86 Android build tools here.
- Agent-side verification should stay lightweight: Gradle configuration,
  dependency metadata, KSP/source checks when available, basic linting, and
  targeted JVM tests that do not require device execution.

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
