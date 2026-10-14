# Extra Quiet

An Android app (Android 10+) that adds an **"Extra quiet"** Quick Settings tile.
When it's on, all audio output is cut by a fixed amount (1–50 dB, default 20),
so the normal volume buttons work across a much quieter range.

## Install

Build the APK (see below), copy it to the phone and open it (allow "install unknown apps"
when asked), or with USB debugging: `adb install app-release.apk`.

Open the app once, allow notifications, and tap **Add Quick Settings tile**
(or add it by editing Quick Settings). Set the amount of reduction with the
slider; long-press the tile to get back to the app.

## How it works

- `QuietEngine` attaches a `DynamicsProcessing` effect with negative input gain
  to audio session 0 (the global output mix). If that fails, it uses an
  `Equalizer` with every band at minimum, which gives a smaller cut (~15 dB).
- It also attaches to individual player sessions that media apps announce
  (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`), for phones that block global effects.
- `QuietService` is a foreground service that keeps the effect alive while it's
  on and shows a notification with a "Turn off" button. Stopping the service
  restores normal volume.
- `QuietTileService` is the toggle tile.

Known limits: if another equalizer/effects app is grabbing the same audio, one
of them will win (the app's status line says "another app has control!"). After
a reboot Extra quiet is off until you turn it on again.

## Store assets

`store/icon-512.png` is the Play Store icon; `store/icon.svg` is the same design
as `app/src/main/res/drawable/ic_launcher_foreground.xml`, on its background.

## Build

```
./gradlew bundleRelease     # -> app/build/outputs/bundle/release/app-release.aab (for Google Play)
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk (for sideloading)
```
Release builds are signed with the upload key named by
`extraquiet.keystoreProperties` in `~/.gradle/gradle.properties` (a properties
file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it
they fall back to the debug key.
Needs JDK 17+ (set `JAVA_HOME`, or `org.gradle.java.home` in
`~/.gradle/gradle.properties`) and the Android SDK (`ANDROID_HOME`, or
`sdk.dir` in `local.properties`).
