# Tape Measure AR

Turns your phone's camera into a measuring tape: point it at real-world surfaces, tap
two points, and it shows the straight-line distance between them in metric units
(centimeters or meters).

## How it works

The app uses Google **ARCore** to track the camera's position in 3D space and to hit-test
where you tap against real surfaces (planes, depth data, or feature points). When you tap
twice, it computes the Euclidean distance between the two tapped 3D points — the same
underlying approach as Google's own Measure app.

1. Launch the app and slowly move your phone so ARCore can detect surfaces.
2. Tap the first point (e.g. one end of what you want to measure).
3. Tap the second point — the distance appears on screen (cm if under 1 m, otherwise m).
4. Tap anywhere again (or the ✕ button) to clear and start a new measurement.

Accuracy depends on ARCore's surface/depth tracking — it's generally within a few
centimeters for typical indoor distances (1–5 m), similar to other AR measuring apps.

## Requirements

- An Android device that [supports ARCore](https://developers.google.com/ar/devices)
  (most Android phones from 2018 onward), running Android 8.0 (API 26) or newer.
- Google Play Services for AR (Play Store installs/updates this automatically on
  first launch if it's missing).

## Building the APK

This sandbox environment cannot reach Google's Maven repository (`dl.google.com`), so the
project can't be compiled here. Two ways to get an installable APK:

### Option A: GitHub Actions (automatic)

Every push triggers `.github/workflows/build-apk.yml`, which builds a debug APK and
uploads it as a workflow artifact named **tape-measure-debug-apk**. Go to the
**Actions** tab on GitHub, open the latest run, and download it from the
"Artifacts" section.

### Option B: Build locally with Android Studio

1. Open this folder in Android Studio (Giraffe/2023.1 or newer).
2. Let Gradle sync (it will download the Android Gradle Plugin, AndroidX, and the
   ARCore SDK from Google's Maven — needs normal internet access).
3. Run the `app` configuration on a physical ARCore-supported device (the emulator
   generally doesn't support ARCore's camera pass-through).
4. Or run `./gradlew assembleDebug` from a terminal; the APK lands in
   `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

- `app/src/main/java/com/tapemeasure/ar/MainActivity.kt` — session lifecycle, tap
  handling, hit-testing, and distance calculation.
- `app/src/main/java/com/tapemeasure/ar/BackgroundRenderer.kt` — draws the live camera
  feed as the GL background.
- `app/src/main/java/com/tapemeasure/ar/PointLineRenderer.kt` — draws the tap markers
  and the line between them.
- `app/src/main/java/com/tapemeasure/ar/DisplayRotationHelper.kt`,
  `CameraPermissionHelper.kt`, `TrackingStateHelper.kt` — small ARCore session helpers.
