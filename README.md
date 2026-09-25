# Native Recorder

A native Kotlin Android recorder using Jetpack Compose, Material 3 and a RemoteViews home-screen widget. Recordings stay in the app’s private `files/recordings/` directory. No account, Internet permission, analytics or cloud backup.

## Build and run

1. Open this repository root in Android Studio (a version supporting Android Gradle Plugin 9.1.1).
2. Use JDK 17 or newer, install Android SDK Platform 35, and let Gradle install any required build tools. The project targets SDK 35 and supports Android 8.0 / API 26 onward.
3. Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/sdk`. Do not commit machine paths or signing credentials.
4. Sync Gradle and run the `app` configuration on a physical device, or build from the root:

   ```sh
   ./gradlew :app:assembleDebug
   ```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The checked-in Gradle wrapper downloads Gradle 9.3.1. AGP 9 provides built-in Kotlin; a separate Kotlin Android plugin is not needed.

## Use

- Choose **Mic** or **Device**, then **Record**. Pause/resume and Stop are available in the app and ongoing notification. Recording continues when you leave the app; playback pauses when you leave it.
- Mic records AAC in M4A. Device audio (Android 10+) records mono 44.1 kHz PCM16 WAV, about 5 MB/minute. Both formats play in the app. Pauses are omitted from the recording.
- Recordings appear newest first with duration, relative date and source. Tap a title to rename it; use Play/Pause to listen. Delete requires confirmation.
- Settings explains storage and system-following theme. Android 12+ uses dynamic wallpaper colors; earlier versions have static light/dark schemes.
- Audio files are finalized on Stop. The app attempts to save captured audio when projection permission ends. Force-stop, process termination or power loss can lose an unfinished session; incomplete `.part` files are discarded at the next process launch. Uninstalling deletes recordings. WAV sessions stop at the format’s approximately 4 GB size limit.

## Permissions and capture limits

Microphone permission (`RECORD_AUDIO`) is required for both sources. Android 13+ asks for notification permission so recording controls are visible; denying it does not block recording. Permission denials offer a Settings link. The service declares microphone and media-projection foreground-service permissions/types, selecting only the active source’s type. A partial wake lock keeps an active recording running with the screen off.

Device recording asks for Android MediaProjection consent **each session**, including widget starts. It captures audio only; no video is saved. Select the whole-screen option if Android offers a choice and you intend to capture across eligible apps.

**Device audio is not a guaranteed Meet/call/VoIP recorder.** Only eligible media/game/unknown playback from apps that allow capture can be captured. Voice-communication audio, protected content, other profiles and apps that opt out may be silent. The app explains this before each device capture and offers Mic as a fallback. Mic can hear a speaker, but quality and availability vary (other apps can also own the microphone). See Android’s [playback capture documentation](https://developer.android.com/media/platform/av-capture) and [foreground service rules](https://developer.android.com/develop/background-work/services/fgs/service-types).

## Widget

Long-press your launcher’s home screen, select **Widgets → Recorder**, and add the **2×2** squircle widget.

The home widget is RemoteViews + bitmap artwork (Nothing-inspired monochrome waveform), not a wordy debug card. **Flick up/down** to swipe between Waveform, Timer and Action pages (same translucent trampoline pattern as the step-counter widgets). **Tap** while idle starts **device audio** recording: the Activity only appears for mic/notification permissions and Android’s MediaProjection consent — it cannot grant capture silently. Once recording, the widget shows a red live `MM:SS` pill and energetic bars, updating about once per second. Swipe to the Action page and **tap to stop** without opening the full app. On Android 8–9 a tap opens the in-app microphone recorder instead.

In-app UI stays Compose Material 3 (light/dark + dynamic color). The widget surface is intentionally stark monochrome for glanceable home-grid density.

## Source layout

`app/src/main/java/com/shashankanil/nativerecorder/` contains `ui/theme`, `ui/home`, `recording`, `storage`, `service` and `widget`. A ViewModel controls playback and service intents; the foreground service owns capture; a repository scans local files and stores atomic JSON title/duration metadata. The single Activity hosts Material 3 dialogs for settings, permissions and destructive actions.

The original research and design document remains at [docs/PLAN.md](docs/PLAN.md).

## Verification

Run JVM WAV-format tests and Android lint with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

### Device verification

Test microphone capture, pause/resume, background/screen-off recording, notification Stop, playback, rename and confirmed deletion. On API 29+ test allowed media playback capture, silence from a capture-blocking app, projection cancellation/revocation and widget consent. On API 33+ test both permission denial paths; on API 26–28 confirm Device is disabled. Check rotation during recording and permission prompts, both system themes and dynamic color on API 31+. A physical device is needed to validate vendor audio behavior and capture eligibility.
