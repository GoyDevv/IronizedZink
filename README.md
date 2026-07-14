# IronizedZink

A Jetpack Compose Android app generated as a complete Android Studio project
(version catalog, theme package, adaptive icons, tests).

## Building
Requires the Android SDK. Install it from the editor's **Dependencies** download dialog,
or point Gradle at an existing SDK via `local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then:

```bash
./gradlew assembleDebug
```

> Build from an exec-capable filesystem (the terminal sandbox), not /sdcard.
