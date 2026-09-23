# Plan: Minimal Material 3 Native Android Recorder

> **Plan only.** Do not treat this document as implemented code. No app modules, Gradle projects, or runtime logic belong in this repo until a future build phase.

**Author account:** shashankanil  
**Target repo:** https://github.com/shashankanil/native-recorder  
**Research date:** 2026-09-23 (IST)

---

## 1. Stack + rationale

### Chosen stack (native path)

| Layer | Choice | Why |
|-------|--------|-----|
| Language | **Kotlin** | First-class Android language; required for modern Jetpack APIs. |
| UI | **Jetpack Compose + Material 3** (`androidx.compose.material3`) | Official native UI toolkit; ships the real Material You component set used by system apps. |
| Theming | **M3 light + dark + dynamic color** | `dynamicLightColorScheme` / `dynamicDarkColorScheme` on API 31+; static `lightColorScheme` / `darkColorScheme` fallback; follow system `isSystemInDarkTheme()`. |
| Widget | **Jetpack Glance App Widget** + **`glance-material3`** | Compose-style API for remote surfaces; Material 3 color roles via Glance theme interop — the Google-supported path for home-screen widgets. |
| Architecture | Single-activity, single primary screen; ViewModel + coroutines; Repository for files | Keeps the product as minimal as Google Recorder’s “one job” UX. |
| Persistence | App-specific storage (`context.filesDir` / `getExternalFilesDir`) + MediaStore optional export later | On-device by default; no cloud. |
| Background | `ForegroundService` with typed FGS + persistent notification | Required for ongoing mic / media-projection capture. |

### Explicit non-goals for UI

- No Flutter / React Native / KMP UI
- No iOS
- **No third-party UI kits** (no Compose Destinations skins, no custom design systems, no Accompanist-as-UI-kit replacements for M3)
- No flashy custom chrome that breaks Material tonal surfaces / ripple / dynamic color

### Why this is the native path

1. **Material 3 in Compose is the platform UI** — documented at [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3); dynamic color is the Material You personalization path.
2. **Google Recorder’s public UX** (large FAB, reverse-chronological list, no bottom nav) is itself Material You — the reference for “super native, super minimal.”
3. **Glance + glance-material3** is the Jetpack-supported way to build App Widgets that share Material 3 color roles with the app ([Glance releases](https://developer.android.com/jetpack/androidx/releases/glance)).
4. Kotlin + Compose is what Android documentation and samples (Reply, etc.) standardize on for new apps.

### Suggested module layout (future build)

```
app/
  ui/theme/          # MaterialTheme + dynamic color
  ui/home/           # Scaffold + list + FAB
  ui/recording/      # In-session surface (same screen or fullscreen overlay)
  ui/permissions/    # M3 AlertDialog / rationale screens
  recording/         # AudioRecord, MediaProjection, AudioPlaybackCapture
  storage/           # On-device file repository
  widget/            # GlanceAppWidget + ActionCallback
  service/           # RecordingForegroundService
```

---

## 2. UX research summary (with sources)

### 2.1 Mobbin — recorder / audio patterns

**Sources**

- [Mobbin: Audio & Video Recorder screens](https://mobbin.com/explore/mobile/screens/audio-video-recorder)
- [Remind Android Voice recorder](https://mobbin.com/explore/screens/5b349e72-e8b7-4416-9937-a295d9c59236)
- [Otter AI Android Recording Interface](https://mobbin.com/explore/screens/c588aa6b-4938-4e99-88ea-cd1721ec7fab)
- [Evernote iOS Audio Recorder](https://mobbin.com/explore/screens/3ea49725-0f9a-4005-81bd-523ad8e47f21)

**Patterns observed (public listings; detailed screenshots may require Mobbin login)**

- **Timer + single primary control** dominate active recording UIs (Remind, OpenPhone, Spotify for Creators).
- **Bottom sheets / overlays** for short voice capture (Clubhouse, Lemonade, Perplexity) — useful for *quick* notes, but for a dedicated recorder prefer a **persistent in-app session** (Google Recorder model) rather than ephemeral sheets.
- **Paused vs active** are visually distinct states (X, How We Feel, Numo).
- **Error + empty** screens exist as first-class patterns (Swiggy voice error; Raycast empty state).
- Otter-style transcript chrome is out of scope for v1 minimal — keep playback simple.

**Recommendation mapped to M3:** one `Scaffold` home, large primary FAB / stop control, `ListItem` library, `AlertDialog` for destructive actions, `Snackbar` for save/errors — not a multi-destination `NavigationBar`.

### 2.2 Dribbble — clean minimal recorder UI

**Sources**

- [Echoic — Voice Recording App (Sahil Dobariya / Heloxone)](https://dribbble.com/shots/27184382-Voice-Recording-App-Echoic) — minimal light concept: bold red record accent, timer, searchable library, playback detail.
- [Recording button / volume control collection](https://dribbble.com/emiwestside8/collections/1623913-Recording-button-Volume-Control-UI-Examples) — many shots emphasize a single circular record control + waveform.

**Access note:** Direct fetch of Dribbble shot pages was **JS/bot-gated** during research (2026-09-23). Findings above are from public search snippets and titles; treat Echoic as *inspiration for hierarchy*, not as a skin to copy. **Map any Dribbble idea into stock M3 roles** (error/primary for record accent via `colorScheme.error` or `primary`, not custom brand kits).

**Filter applied:** Prefer shots that map to Material (single FAB, list + detail, restrained accent) over flashy custom kits / neumorphism / glassmorphism.

### 2.3 Awwwards / awards — minimal mobile UX

**Sources**

- [Awwwards — Mobile & Apps category](https://www.awwwards.com/websites/mobile-apps/)
- Nominees/HM examples emphasizing clean presentation (e.g. utility-oriented showcases such as Grassfeld budgeting HM, VOID Messenger HM — browse category for current set)

**Takeaway for this product**

Awwwards skews **marketing-site polish**, not Android system chrome. Steal the *principles*, not the web chrome:

- One primary action
- Generous whitespace / clear hierarchy
- Motion that clarifies state, not decoration
- Content-first surfaces

On Android, those principles land as **Material tonal surfaces + single FAB + sparse TopAppBar**, not custom web-like canvases.

### 2.4 Official Material 3 / Material You (recording-adjacent)

**Sources**

- [FAB overview (M3)](https://m3.material.io/components/floating-action-button/overview) / [FAB menu](https://m3.material.io/components/fab-menu/overview) — primary action; use FAB menu only if 2–6 related actions are needed (e.g. Mic vs Device audio). Prefer a **FilterChip / SegmentedButton row** for source mode to keep one FAB.
- [Lists overview](https://m3.material.io/components/lists/overview) / [guidelines](https://m3.material.io/components/lists/guidelines) — scannable `ListItem` with leading media/icon, title, supporting duration, trailing play/overflow.
- [Empty states (Material patterns)](https://m1.material.io/patterns/empty-states.html) — non-interactive illustration + purpose tagline; avoid cluttered starter kits for a recorder.
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — dynamic color, light/dark, tonal elevation, component roles.
- Google Recorder writeups: [Material You redesign](https://9to5google.com/2021/10/20/pixel-recorder-material-you-redesign/), [3.5 QS tile](https://9to5google.com/2022/03/02/google-recorder-3-5-pixel/), [Ode to Pixel Recorder](https://9to5google.com/2023/12/29/ode-to-pixel-recorder/) — large FAB, reverse-chronological list, **no drawer / bottom bar / tabs**, recording continues with notification when screen sleeps.

### 2.5 Concrete UX recommendations (product)

1. **Layout:** Single screen — `Scaffold` + `TopAppBar` (title “Recorder”, overflow for Settings) + `LazyColumn` of recordings + docked large `FloatingActionButton` (or bottom-centered filled stop button while recording).
2. **Recording states:** Idle → Recording → Paused → Saving. Use `CircularProgressIndicator` / subtle level meter on `surfaceContainer` Card; timer as `displaySmall` / `headlineMedium`. Stop uses filled tonal / error emphasis per M3, not a custom red skin.
3. **Source mode:** `FilterChip` or `SingleChoiceSegmentedButtonRow`: **Microphone** | **Device audio**. Device audio explains MediaProjection once via `AlertDialog`.
4. **Permissions:** In-context rationale (`AlertDialog` / educational `Card`) → system permission → if denied, `Snackbar` with Settings deep link. Never dark-pattern.
5. **Widget:** Compact Glance widget: Material-styled filled button “Record device audio” + small status text (Idle / Recording).
6. **Storage presentation:** List items show title (editable), duration, relative date, source badge (Mic / Device). Files live in app storage; optional “Share” / “Export” later via system sharesheet — not cloud sync in v1.
7. **Empty state:** Centered icon (`Icons.Outlined.MicNone`) + tagline “Tap record to capture audio on this device” + no fake demo files.
8. **Prefer single-screen minimal** — no `NavigationBar` until a second destination is truly needed (Settings can be a second route via TopAppBar).

---

## 3. App structure

### Screens (minimal)

| Surface | Role | M3 building blocks |
|---------|------|--------------------|
| Home | Library + primary record CTA | `Scaffold`, `TopAppBar`, `LazyColumn`/`ListItem`, `FAB`, empty state |
| Active recording | Same scaffold morph or fullscreen overlay | Timer, pause/resume `FilledIconButton`s, stop `Button`, optional waveform on `Card` |
| Permission / mode explainers | Modal | `AlertDialog`, maybe one-time `ModalBottomSheet` |
| Settings (thin) | Theme follow-system (default), storage path info | `ListItem`, `Switch` only if needed |
| Widget | Start device-audio recording | Glance `Button` / `IconButton` + `GlanceTheme` (material3) |
| Foreground notification | Ongoing session | System notification (not custom UI kit) |

### Navigation

- Prefer **zero bottom nav**.
- Optional: `rememberNavController` only for Settings / playback detail if playback needs a second pane; otherwise inline expand / simple detail `AlertDialog` is enough for v1.

### Data flow (conceptual)

```
UI (Compose) → ViewModel → RecordingController → ForegroundService
                              ↓
                         FileRepository (on-device)
Widget (Glance ActionCallback) → same RecordingController / Service
```

---

## 4. Permissions model (honest)

### Reality check: “Google Meet–like” / call audio

Android **does not** give third-party apps a clean, unrestricted “record any call / Meet” API.

| Approach | What it captures | Limits |
|----------|------------------|--------|
| **Mic (`RECORD_AUDIO`)** | What the microphone hears (speakerphone Meet audio if loud enough) | No true internal mix; privacy/UX caveats; quality varies. |
| **`MediaProjection` + `AudioPlaybackCapture` (API 29+)** | Mix of **other apps’ playback** that use `USAGE_MEDIA` / `USAGE_GAME` / `USAGE_UNKNOWN` and allow capture | **Cannot** capture usages like `USAGE_VOICE_COMMUNICATION` (typical VoIP). Capturing apps must share user profile; user must approve the projection prompt; target apps may opt out. Official docs: [Capture video and audio playback](https://developer.android.com/media/platform/av-capture). |
| **Google Meet Media API** | Authorized Meet conference media | Restricted OAuth; enterprise/partner path — **out of scope** for a personal minimal recorder. |

**Product honesty for this plan**

- Market **device/internal audio** as: *system media / game / allowed playback capture*, not “guaranteed Meet/call recorder.”
- For Meet: mic-from-speaker is the realistic fallback; true VoIP tap is generally blocked.
- Always offer **Microphone** mode that works without MediaProjection.

### Required permissions & types (planned)

| Permission / declaration | Why |
|--------------------------|-----|
| `RECORD_AUDIO` | Mic + playback-capture pipeline |
| `POST_NOTIFICATIONS` (API 33+) | Ongoing recording notification |
| `FOREGROUND_SERVICE` | Long-running capture |
| `FOREGROUND_SERVICE_MICROPHONE` | Mic FGS type (Android 14+ rules) |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Projection-based capture FGS type |
| MediaProjection user consent (runtime Intent) | Required every session (or until revoked) for playback capture — see [Media projection](https://developer.android.com/media/grow/media-projection) |
| Optional: `WAKE_LOCK` | Keep recording reliable — prefer FGS + careful audio focus instead if possible |

### Flow

1. First launch: short privacy card — “Audio stays on this device.”
2. Tap Record (Mic): request `RECORD_AUDIO` → start FGS (`microphone`) → `AudioRecord` from mic.
3. Tap Record (Device audio): rationale dialog → `RECORD_AUDIO` if needed → `createScreenCaptureIntent()` / MediaProjection consent → start FGS (`mediaProjection` ± `microphone` if mixed) → `AudioPlaybackCaptureConfiguration` + `AudioRecord`.
4. On projection revoke (`MediaProjection.Callback.onStop`): stop cleanly, `Snackbar` “Screen/audio capture permission ended.”
5. Widget path: must bring user through MediaProjection consent if no valid projection token — typically launch a transparent/`Activity` to obtain consent, then start service (cannot silently grant projection from a widget alone).

### Mic-only fallback

If projection denied or API < 29: disable Device chip with supporting text, keep Mic fully functional.

---

## 5. Widget design (Glance)

### Goals

- One tap intent: **start device-audio recording** (primary ask).
- Material-styled via `androidx.glance:glance-material3` and `GlanceTheme`.
- Show Idle / Recording state.

### Layout (compact / modern size)

```
┌─────────────────────────────┐
│  Native Recorder            │
│  [● Record device audio]    │  ← Glance Button (primary)
│  Status: Idle               │
└─────────────────────────────┘
```

When recording:

```
│  [■ Stop]                   │
│  Status: Recording 01:24    │
```

### Implementation notes (future)

- `GlanceAppWidget` + `GlanceAppWidgetReceiver`
- `actionRunCallback` / `actionStartActivity` to obtain MediaProjection then start `RecordingForegroundService`
- Update widget state with `PreferencesGlanceStateDefinition` (recording flag, elapsed optional)
- Colors from `GlanceTheme.colors` (material3) so light/dark + dynamic wallpaper alignment matches the app where the platform allows

### Why Glance (not RemoteViews XML-only)

Compose-like API, official Material 3 interop module, maintained Jetpack path for App Widgets.

---

## 6. On-device storage

### Defaults

- Store under app-specific directory, e.g. `files/recordings/` or `getExternalFilesDir(Environment.DIRECTORY_MUSIC)`.
- Format: AAC/M4A via `MediaCodec`/`MediaRecorder` or PCM→AAC pipeline (decide at build time; prefer widely playable **M4A**).
- Metadata: filename, createdAt, durationMs, source (`MIC` | `DEVICE`), optional display title in a tiny local DB (DataStore or Room) — Room only if list queries need it; DataStore + file scan may suffice for v1.

### Presentation

- Reverse-chronological `ListItem`s (Google Recorder pattern).
- Swipe-to-delete optional later; v1: trailing `IconButton` → confirm `AlertDialog`.
- No cloud, no auto-upload, no account.

### User trust copy

Empty state + Settings: “Recordings never leave this device unless you Share them.”

---

## 7. Clean minimal UX spec (Material 3 components only)

### Theme

```text
AppTheme
  darkTheme = isSystemInDarkTheme()
  colorScheme =
    if (API >= 31) dynamicLight/DarkColorScheme(context)
    else lightColorScheme() / darkColorScheme()
  MaterialTheme(colorScheme, typography = Typography(), shapes = Shapes())
```

- Respect system light/dark.
- Dynamic color on Android 12+.
- Use tonal surfaces (`surface`, `surfaceContainer`, `primaryContainer`) — not flat custom blacks/whites that fight Material You.
- Record emphasis: prefer `colorScheme.error` for *stop* / hard record affordance sparingly; primary FAB for start is fine (matches many M3 apps). Avoid non-theme hard-coded `#FF0000` skins.

### Component map

| Need | Component |
|------|-----------|
| Page frame | `Scaffold` |
| Title / overflow | `TopAppBar` / `CenterAlignedTopAppBar` |
| Start recording | `FloatingActionButton` or `ExtendedFloatingActionButton` |
| Pause / resume / delete | `FilledIconButton` / `FilledTonalIconButton` / `IconButton` |
| Library rows | `ListItem` (+ `HorizontalDivider` if needed) |
| Source mode | `FilterChip` or `SingleChoiceSegmentedButtonRow` |
| Confirm delete / projection rationale | `AlertDialog` |
| Transient feedback | `Snackbar` |
| Grouping timer / meter | `Card` / `ElevatedCard` |
| Settings rows | `ListItem` + `Switch` |
| Widget | Glance `Button`, `Text`, `Row`/`Column` under `GlanceTheme` |

### Recording state machine (UI)

1. **Idle** — FAB visible; chips enabled; list scrollable.
2. **Recording** — FAB replaced by Stop; Pause visible; chips disabled; `TopAppBar` may show live duration; FGS notification mirrors state.
3. **Paused** — Resume + Stop; timer frozen.
4. **Saving** — brief indeterminate indicator; then Snackbar “Saved”; return Idle.

### Motion / feedback

- Rely on M3 ripple and container transforms; no third-party animation kits required for v1.
- Optional subtle canvas level meter using Compose `Canvas` is OK (drawing, not a UI kit).

### Accessibility

- Content descriptions on all icon buttons (“Start recording”, “Stop”, “Play”).
- Minimum 48dp touch targets (M3 defaults).
- Don’t convey recording state by color alone — include text (“Recording”).

---

## 8. Build phases (future — not this repo)

1. Skeleton app + M3 theme (light/dark/dynamic) + empty Home.
2. Mic recording + FGS + file list playback via `MediaPlayer` / ExoPlayer (player only; still M3 chrome).
3. MediaProjection + AudioPlaybackCapture path + honest UX copy.
4. Glance widget for device-audio start.
5. Polish empty/error/permission copy; optional QS tile later (out of v1).

---

## 9. Success criteria for *this* plan repo

- [x] Documents Material 3 light/dark + dynamic color + standard components only
- [x] Stack centered on Kotlin + Compose Material3 + Glance
- [x] UX research cited (Mobbin, Dribbble, Awwwards, M3, Google Recorder, Android capture docs)
- [x] Honest permissions / Meet limitations
- [x] Plan only — no app implementation

---

## Appendix — key official links

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/jetpack/androidx/releases/glance
- https://m3.material.io/components/fab-menu/overview
- https://m3.material.io/components/lists/overview
- https://developer.android.com/develop/background-work/services/fgs/service-types
