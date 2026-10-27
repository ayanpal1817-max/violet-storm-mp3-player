# Violet Storm MP3 Player — starter 0.1

Native Android / Wear OS source project for Samsung Galaxy Watch 4 Bluetooth.

## Important status
This is a **starter source project**, not a verified APK. It has not been compiled in this environment because no Android SDK or Gradle installation is configured here. Do not install it until Android Studio finishes a successful build.

## What this starter includes
- Kotlin + Jetpack Compose
- Media3 ExoPlayer + MediaSessionService for playback that can continue outside the activity
- Local audio selection using Android's system file picker
- Basic previous / play-pause / next and seek controls
- Purple-on-black circular visual treatment and animated bars
- Menu routes for Library, Albums, Favourites, and Equalizer
- Shuffle/repeat toggles

## Not finished yet
- Persisting the chosen track list after app restart
- Proper metadata/album-art extraction and folder browsing
- Saved favourites and playlist management
- Real five-band audio effect / equalizer DSP
- Real circular volume dial and refined watch-size layout
- Full UI testing and compatibility/build verification on a Galaxy Watch 4

## Build in Android Studio
1. Install Android Studio on your Windows desktop.
2. Open this folder with **File → Open** (select the `VioletStormMP3Player` folder).
3. Let Android Studio sync Gradle and download the Android SDK/platform components it requests. Use JDK 17 if Android Studio asks.
4. If prompted to install Android SDK Platform 35 and Build Tools, accept.
5. Choose **Build → Make Project**. Fix any errors shown before proceeding.
6. After a successful build, use **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
7. The debug APK should appear at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on Galaxy Watch 4
1. On the watch, open Settings → About watch → Software information.
2. Tap **Software version** repeatedly until Developer options are enabled.
3. In Settings → Developer options, enable **ADB debugging** and **Debug over Wi-Fi** (labels vary by Wear OS version).
4. Connect the watch and Windows PC to the same Wi-Fi network. On the watch's Debug over Wi-Fi screen, note the displayed IP address and port.
5. On the PC, install Android SDK Platform-Tools (ADB). In a terminal run:
   `adb pair WATCH_IP:PAIRING_PORT`
   Enter the pairing code shown on the watch.
6. Then run:
   `adb connect WATCH_IP:DEBUG_PORT`
   Use the separate connection port shown on the watch.
7. Install:
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`
8. Open Violet Storm on the watch. Pair your Bluetooth earbuds directly in the watch's Bluetooth settings, then select an audio file stored on the watch.

## Offline behavior
The app does not require internet at runtime to play local audio. Internet may be required on the PC for the first Gradle sync to fetch dependencies.
