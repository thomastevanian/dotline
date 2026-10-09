# Dotline

**A flat, dotted, monochrome home screen for Samsung phones, styled after Nothing OS 5.0.**

Dotline is a launcher app (`com.dotline.launcher`) written in Kotlin and Jetpack Compose. It replaces the
Samsung One UI home screen with a black, dot-matrix look: dark circular icons, a dot-matrix clock, fine
dot-matrix widgets, dot page indicators, a flat app drawer, and tools to make your own dotted wallpapers
and electronic sounds. No ads, no analytics, no tracking, no account. The only network request in the app is
the weather lookup (Open-Meteo), made only when the home screen opens and only if you set a city or allow
location.

> Dotline recreates the *look and feel* with original, programmatically drawn artwork and open-source fonts.
> It contains no Nothing fonts, wallpapers, icons, sounds or logos, and it is not affiliated with Nothing.

## What is inside

| Area | What you get |
| --- | --- |
| Home screen | 5 x 6 grid (4 x 5 up to 6 x 7), up to 7 pages, 4-app dock without a panel, dot page indicators, home search pill, edit mode, drag and drop between pages / dock / folders, folders with 2 x 2 mini icons, uninstall by dragging |
| Widgets | Dot-matrix clock, date, weather, battery, next calendar event, world clock (2 cities), quick note. Circle, capsule and card shapes. Any normal Android app widget can be added too |
| Icons | Monochrome Nothing-style icons (white glyph on a black circle), 60+ hand-drawn original glyphs, automatic silhouettes for everything else, original icons, accent mode, circle or rounded square, size and label options, third-party icon packs, opt-in notification dots |
| App drawer | Swipe up, search as you type, A-Z scroller with floating letter, long-press menu with app shortcuts, hide apps (optional fingerprint lock), optional recents row |
| Wallpaper Studio | Procedural dot wallpapers at your phone's exact resolution (grid, gradient, rings, dot text, halftone, single red dot), 24 presets, apply to home, lock or both |
| Sound Studio | 32 original synthesised sounds (8 ringtones, 12 notifications, 6 alarms, 6 UI clicks): preview, save to the system sound folders, set as default |
| Widgets for any launcher | Real app widgets (dot clock, date, battery, weather) that also work in One UI |
| Settings | Nothing-style settings, backup and restore of your whole layout and settings as a JSON file, crash log screen, "Finish the look" guide |

## Status

Every push is compiled, shrunk with R8, signed and unit-tested by GitHub Actions (230 unit tests cover the layout
engine, drag and drop rules, gestures, drawer search, weather parsing, backup, wallpaper generation and sound
synthesis). The screens themselves have been built from reference screenshots of Nothing OS 5.0 but have not yet been
checked on every phone, so if something looks off or crashes, open **Settings > About > Crash log** and send what it
shows.

## Install on your phone

1. On the phone, open the **[Releases page](https://github.com/thomastevanian/dotline/releases)** in the browser and open the newest release.
2. Download **`Dotline.apk`** (the other file, `Dotline-fallback-debug.apk`, is only for the rare case that the
   main one crashes on launch).
3. Open the downloaded file. Android will ask to allow installs from your browser or Files app
   (**Settings > Install unknown apps**). Allow it for that app only, then tap **Install**.
4. Open **Dotline**. A short three-screen intro explains the look and asks you to make Dotline your home app.

Every build is signed with the same key, so you can install a newer `Dotline.apk` over an older one without
uninstalling, and your layout and settings are kept.

## Make Dotline your default launcher

- Follow the prompt at the end of the intro (Android shows "Set Dotline as default home app"), or
- Go to **Settings > Apps > Choose default apps > Home app** and pick **Dotline**, or
- Press **Home** after installing; Android may ask which launcher to use: choose **Dotline** and **Always**.

## Switch back to Samsung One UI Home

Go to **Settings > Apps > Choose default apps > Home app** and pick **One UI Home**. Dotline stays installed
and your layout is kept for later. You can also uninstall Dotline at any time; the system falls back to
One UI Home on its own.

## What Samsung always controls

Dotline restyles the home screen, drawer, icons, widgets, wallpapers and sounds. One UI itself keeps control
of: the notification shade and quick settings, the Settings app, system animations and transitions, the lock
screen and always-on display, the status bar, navigation gestures and the recent-apps screen. The Glyph
lights exist only on Nothing phones. The in-app **Finish the look** guide links to the Samsung settings that
bring those parts as close as possible (dark mode, colour palette off, lock-screen clock style, always-on
display, edge panels and Bixby off, animation scale 0.5x, system font).

## Permissions (all optional)

Dotline works without granting anything. Each permission is requested only at the moment a feature needs it,
after an explanation.

| Permission | Used for |
| --- | --- |
| Internet | Weather from Open-Meteo, the only network call. Fetched only when the home screen opens, cached 30 minutes |
| Approximate location (runtime, opt-in) | Local weather instead of a typed city; last known coarse location only |
| Calendar (runtime) | The "next event" widget, requested when you add that widget; read only when the home screen resumes |
| Notification access (special, opt-in) | Red notification dots. Dotline sees which apps have a notification, never the content |
| Accessibility service (special, opt-in) | Only for double-tap to lock the screen. Reads nothing |
| Modify system settings (special) | Only when you press "Set as default" in Sound Studio |
| Uninstall packages | Uninstall from the long-press menu or by dragging to Uninstall |
| Expand status bar | Pull down the notification shade with a swipe (the gesture hides itself if the phone refuses) |
| Set wallpaper | Wallpaper Studio |
| Biometric | Optional lock for hidden apps |

## Battery

No foreground or background service, no wake locks, no alarms, no looping animations. The clock follows the
system minute tick only while the home screen is visible; the battery widget reads the sticky battery
broadcast only while visible; weather is fetched only when the launcher comes to the foreground and cached for
30 minutes; the calendar is read only on resume. The app widgets update on the system schedule only (the clock
and date widgets are self-updating system clocks).

## Signing key (committed on purpose)

This is a personal app, so one keystore is committed so that every APK (debug and release) has the same
signature and updates install over each other:

| | |
| --- | --- |
| File | `keystore/dotline.keystore` (PKCS12) |
| Alias | `dotline` |
| Store password | `dotline-release` |
| Key password | `dotline-release` |
| SHA-256 certificate fingerprint | `E0:85:44:9C:BE:73:7A:58:8E:9E:1C:7D:6F:42:B3:74:29:CA:40:A9:95:6E:F6:AF:16:81:32:F3:E9:67:E2:6F` |

Because the key is public, do not rely on this signature for anything security-sensitive. Fork and replace the
keystore if you publish your own build.

## How it is built

GitHub Actions (`.github/workflows/build.yml`) builds on every push to `main` (and to `claude/**` branches
while developing) and can be started manually: JDK 17, Gradle 9.6, Android Gradle Plugin 9.4, Kotlin 2.4,
Compose BOM 2026.09, compile and target SDK 37, minimum SDK 26. The release build uses R8 minification and
resource shrinking. After a green build the workflow publishes a GitHub Release `v1.0.<run number>` with
`Dotline.apk` attached, and uploads the same APKs as a workflow artifact.

There is no need for Android Studio. To build locally anyway:
`./gradlew assembleRelease` (APK in `app/build/outputs/apk/release/`).

## Troubleshooting

- **Something crashed.** Open **Settings > About > Crash log**. It shows the last errors with a Copy and Share
  button; nothing leaves the phone unless you share it.
- **The release APK will not start.** Install `Dotline-fallback-debug.apk` from the same release (same
  signature, no code shrinking) and send the crash log.
- **Weather shows "--".** Set a city in **Settings > Clock and widgets**, or allow approximate location.
- **Icons look wrong after changing the style.** Settings > Icons > the new style applies immediately; the
  first scroll of a big drawer processes each icon once, then it is cached.

## Fonts and licences

Doto, Space Mono and Space Grotesk are bundled under the SIL Open Font License 1.1 (see `licenses/`). Doto is
the nearest open-source match to Nothing's dot-matrix typeface.

## Project layout

```
app/src/main/kotlin/com/dotline/launcher/
  data/         settings, layout engine, repositories, icons, weather, backup
  home/         pure, unit-tested home logic: controller, gesture engine, geometry
  drawer/       app search and A-Z section logic, recents
  ui/           Compose screens and components (theme, home, drawer, settings, studios, guide)
  widgets/      app widgets that work in any launcher
  service/      opt-in notification-dot and lock-screen services
  wallpaper/ sound/   dot-field generator and synthesiser
```

Unit tests live in `app/src/test` and cover the layout engine, drag and drop rules, gesture handling, drawer
search, weather parsing, backup, wallpaper generation and sound synthesis.
