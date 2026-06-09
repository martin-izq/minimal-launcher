# Minimal — a minimalist Android launcher with Screen Time

A text-only, black-and-white Android launcher built with **Kotlin + Jetpack Compose**,
designed to reduce screen time. No icons, no clutter — just the apps you need and gentle
friction against the ones you don't.

> UI language follows the system locale (English and Spanish included).

## Features

- **Text-only home**: large clock, date and optional battery. No icons.
- **Reorderable favorites** on the home screen (move ↑/↓).
- **Alphabetical app drawer** with live **search** (Enter opens the first result) and an
  optional **A–Z scrubber**. Search bar position (top/bottom) is configurable.
- **Real third-party widgets** hosted via `AppWidgetHost`: swipe to a dedicated widgets
  screen, add/remove, reorder and resize their height. No system permissions required.
- **Quick launch**: swipe toward the side opposite the widgets to open a configurable app,
  without moving the home screen.
- **Reduce distractions**: mark apps as *distracting* and a **friction screen** appears
  ("Open X for sure?") with a configurable countdown (3/5/10 s) showing today's usage.
- **Screen Time**: total time today, **unlock count**, a **7-day chart** and a **per-app
  ranking**.
- **Gestures**: swipe up for the drawer, swipe down to expand notifications, double-tap to
  lock the screen, long-press to open settings.
- **Customization**: clock/date/favorites size, alignment, vertical position, AMOLED dark
  or light theme, widgets side, and more.
- **Rename** and **hide** apps (renames only affect favorites).
- The **back button** never leaves the launcher (it returns home).

## Tech stack

- **Kotlin** 2.0.21 · **Jetpack Compose** (BOM 2024.12.01) · **Material 3**
- MVVM with a single `Activity`, a `LauncherViewModel` exposing a single
  `StateFlow<LauncherUiState>` built by combining repositories with `combine()`
- **DataStore Preferences** for settings; **kotlinx.serialization** (JSON) for complex types
- `AppWidgetHost` for hosting third-party widgets
- A minimal `AccessibilityService` for the notification shade and screen lock
- `compileSdk` 35 · `minSdk` 26 · `targetSdk` 35 · JDK 17 · AGP 8.7.2

## Architecture

```
PackageManager     ──► AppRepository        ──┐
DataStore          ──► SettingsRepository   ──┤
DataStore          ──► WidgetsRepository    ──┼─► LauncherViewModel ─► UI (Compose)
UsageStatsManager  ──► UsageStatsRepository ──┘      (LauncherUiState)
```

`LauncherRoot` hosts a `HorizontalPager` (widgets ↔ home) whose home page wraps a
`VerticalPager` (home ↕ app drawer). Screen Time and Settings are modal overlays.

## Building

1. Open the `MinimalLauncher/` folder in **Android Studio** (Ladybug or newer). On sync,
   Android Studio generates the Gradle wrapper automatically.
   - From the terminal (with Gradle 8.9+): `gradle wrapper` then `./gradlew assembleDebug`.
2. Connect a device (USB debugging on) and hit **Run ▶**, or install the APK at
   `app/build/outputs/apk/debug/app-debug.apk`.

## After installing

1. **Set it as the default launcher**: press Home and choose *Minimal*, or go to
   *Settings → Set as default launcher*.
2. **Enable Screen Time**: the first time, *Screen Time → Grant access* takes you to
   *Usage access* in Android settings. Enable *Minimal*. (This is a special permission the
   system does not grant automatically.)
3. **Enable gestures** (optional): the notification-shade and screen-lock gestures rely on
   a small accessibility service you enable from *Settings → Notifications on swipe down*.

## Notes

- App list via `PackageManager.queryIntentActivities` (LAUNCHER intent).
- Usage time via `UsageStatsManager.queryAndAggregateUsageStats`; unlocks counted from
  `KEYGUARD_HIDDEN` events.
- `QUERY_ALL_PACKAGES` is declared — a Google Play–allowed use for launchers, but keep it
  in mind if you publish.

## Project structure

```
app/src/main/java/com/martin/minimallauncher/
├── MainActivity.kt            # Compose host; HOME/back/resume; widget host lifecycle
├── LauncherViewModel.kt       # combined UI state + actions
├── data/                      # AppInfo, AppRepository, SettingsRepository,
│                              #   UsageStatsRepository, WidgetsRepository
├── service/                   # NotificationAccessibilityService
├── ui/                        # LauncherRoot, HomeScreen, AppDrawer, SettingsScreen,
│   ├── theme/                 #   ScreenTimeScreen, shared Components, Modifiers
│   └── widgets/               # WidgetController, WidgetScreen, WidgetPicker, host view
└── util/                      # TimeFormat, NotificationShade
```
