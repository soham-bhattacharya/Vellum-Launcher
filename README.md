<p align="center">
  <img src="docs/assets/vellum-hero.svg" alt="Vellum Launcher — home, with atmosphere" width="100%" />
</p>

# Vellum Launcher

**Home, with atmosphere.** Vellum is a fast, open-source Android launcher that pairs the power and compatibility of Lawnchair with a calmer, more tactile visual language.

[![Android CI](https://github.com/soham-bhattacharya/Vellum-Launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/soham-bhattacharya/Vellum-Launcher/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-9d86ff.svg)](LICENSE.txt)
[![Android](https://img.shields.io/badge/Android-8.0%2B-6da5ff.svg)](#install)

> [!IMPORTANT]
> **Bloom Preview 1** is an enthusiast preview built from Lawnchair's Android 16 development branch. Back up an existing launcher layout before making Vellum your default home app.

## The Vellum experience

- **Ambient Canvas** — a wallpaper-aware field of soft light, fine orbit lines, and page-linked parallax that redraws only when the workspace moves.
- **The Halo** — Vellum's small signature control in the top-right corner. Tap it for All Apps; hold it for settings.
- **Bloom Reveal** — a cinematic, one-time welcome that introduces the launcher without adding another setup maze.
- **Folded identity** — a new adaptive and monochrome icon inspired by a sheet folding into a `V`.
- **Pixel-fast foundation** — Launcher3 recents, global search, icon packs, gestures, folders, Smartspace, backup/restore, and Lawnchair's deep customization remain available.
- **Motion with manners** — Vellum observes Android's system animation setting, stops its reveal animation when dismissed, and hides all ambient work outside the normal home state.

The ambient layer has no network access, telemetry, service, or background timer. Vellum inherits Lawnchair's optional online search and update-related components; network search runs only through features the user chooses to use.

## Install

1. Download the newest APK from [Releases](https://github.com/soham-bhattacharya/Vellum-Launcher/releases).
2. Allow installation from your browser or file manager when Android asks.
3. Open **Vellum**, tap **Enter Vellum**, then choose it as the default Home app.

The GitHub build uses the independent package `app.vellum.launcher`, so it can coexist with official Lawnchair. Preview/debug builds use `app.vellum.launcher.debug`.

## Build it yourself

Prerequisites: JDK 21, the Android SDK, and Git with submodule support.

```bash
git clone --recurse-submodules https://github.com/soham-bhattacharya/Vellum-Launcher.git
cd Vellum-Launcher
./gradlew assembleLawnWithQuickstepGithubDebug
```

The APK is written to `build/outputs/apk/lawnWithQuickstepGithub/debug/`.

For an optimized build:

```bash
./gradlew assembleLawnWithQuickstepGithubRelease
```

Without `keystore.properties`, release builds fall back to the local Android debug key and are suitable for personal testing—not store distribution.

## Design and performance notes

Vellum's home effects are native Android `Canvas` drawing rather than a WebView, bitmap animation, or live wallpaper. Shaders are built on size/theme changes and translated by the GPU during page motion. The page listener invalidates a single non-interactive view, and launcher-state callbacks fade and disable it as soon as All Apps, Overview, or an app becomes active.

The one-time welcome animation honors `ValueAnimator.areAnimatorsEnabled()`, cancels on detach, and permanently removes itself from the view hierarchy after dismissal.

## Lineage

Vellum is a fork of [Lawnchair 16](https://github.com/LawnchairLauncher/lawnchair), which is based on Android's Launcher3. The first Vellum release began at upstream commit [`d1fa12d`](https://github.com/LawnchairLauncher/lawnchair/commit/d1fa12df1951b92c1c0e9c06b4a0815d683b5260).

The Lawnchair project and its contributors created and maintain the substantial launcher foundation beneath Vellum. Please support the [Lawnchair project](https://opencollective.com/lawnchair) and review its [documentation](https://docs.lawnchair.app/) for advanced launcher features.

## License

Vellum, Lawnchair, and Launcher3 code in this repository are distributed under the [Apache License 2.0](LICENSE.txt), with copyright notices retained in their respective source files.
