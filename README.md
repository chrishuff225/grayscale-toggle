# Smart Grayscale

A tiny Android app that turns your phone's screen black & white (system-wide
grayscale) and back, with two extras:

1. **Apps to keep in color** — pick apps (via the ⚙️ settings icon) that should
   never be grayscale. When one of them is open the screen returns to color, and
   it goes back to grayscale when you leave.
2. **Pause timer** — switch back to color for a set number of minutes; grayscale
   turns itself back on automatically when the timer ends.

## Install on your phone

1. On your phone, open the latest build and download `grayscale-toggle.apk`:
   **https://github.com/chrishuff225/grayscale-toggle/releases/latest**
2. Open the downloaded file. If prompted, allow installing from unknown sources.

## One-time setup

Android does not let an ordinary app change system grayscale on its own, so you
grant one permission once using ADB from a computer. After this, the app works on
its own forever — no computer needed again.

1. Install [ADB / platform-tools](https://developer.android.com/tools/releases/platform-tools)
   on a computer and enable **USB debugging** on the phone
   (Settings → About phone → tap Build number 7 times → Developer options → USB debugging).
2. Plug the phone into the computer and run:

   ```
   adb shell pm grant com.chrishuff.grayscale android.permission.WRITE_SECURE_SETTINGS
   ```

   (The app shows this exact command with a "Copy" button if you forget.)
3. In the app, tap **Open accessibility settings** and enable **Smart Grayscale** —
   this is what lets the "keep in color" app list work. If the toggle is greyed out
   (common for sideloaded apps on Android 13+), first open the app's **App info**
   page, tap the **⋮** menu, and choose **"Allow restricted settings"**. The app has
   a button that takes you straight there.

## Using it

- Flip the **Grayscale** switch to turn black & white on or off.
- Tap the **⚙️ gear** (top right) to choose apps that stay in color.
- Enter minutes and tap **Pause grayscale** to take a color break; it resumes
  automatically, or tap **Resume grayscale now** to end the break early.

## How it works

- Grayscale is the system "monochromacy" daltonizer setting, toggled via
  `WRITE_SECURE_SETTINGS`.
- An accessibility service detects the foreground app to apply the exclusion list.
  It only reads the package name of the foreground window — no screen content.
- The pause timer uses `AlarmManager`, so it still fires if the app is closed.

## Building

Every push to `main` builds a debug APK via GitHub Actions
(`.github/workflows/build.yml`) and publishes it as a release. To build locally
you need JDK 17 and the Android SDK, then run `gradle assembleDebug`.
