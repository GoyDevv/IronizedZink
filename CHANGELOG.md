# Changelog — Ironized Zink

All notable changes to **Ironized Zink** (by GoyDevv). Versions follow the app's
`versionName`. Every release is signed with the same key, so it installs as an update.

---

## v1.1.0 — 2026-07-15

Bug-fix, optimization and UI overhaul pass, grounded in Mesa's own documentation and
real Android APIs (no invented/fake options).

### 🩹 Fixed
- **Hand rendering through GUI screens.** Root cause: **Potato** and **Performance**
  shipped `MESA_NO_ERROR` (no-error fast path) together with `allow_draw_out_of_order`
  by default. Mesa's own docs state `MESA_NO_ERROR` causes **"undefined behavior for
  invalid use of the API"** — combined with out-of-order draw reordering, this let the
  driver legally skip/reorder the depth-clear boundary between the world/hand pass and
  the GUI overlay pass, which is exactly what Minecraft's GUI rendering relies on being
  precise. Fix: no preset combines the two by default any more. Out-of-order drawing
  (safe on its own, a real perf win) stays on for the fast presets; **No-error fast
  path** is now an explicit, clearly-labelled **Danger zone** toggle, and the UI shows a
  live warning banner if you ever turn both on together yourself.

### 🚀 Changed / real optimization
- **Potato now caps to your display's real refresh rate** (60 Hz / 90 Hz / 120 Hz /
  144 Hz — detected live via the Android `Display` API) instead of running fully
  uncapped. Mesa/Zink has **no environment variable for an arbitrary numeric FPS
  target** — the only real driver-level mechanism is VSync (`vblank_mode` /
  `FORCE_VSYNC`), so that's what this cap honestly uses; there is no fake "target FPS"
  slider. Running uncapped past what the screen can display only burns battery/heat, so
  Potato now syncs to the panel instead.
- **Performance** keeps out-of-order drawing and big-core affinity, uncapped by default,
  without the unsafe no-error combination.

### 🎨 Redesigned
- **Categorized settings UI**: Presets, **Performance** (OpenGL version, threading,
  affinity, out-of-order, refresh-rate cap), **Shaders & compatibility**, and a
  **Danger zone** section for the genuinely riskier knobs (no-error, force-software),
  each with a header + subtitle instead of one long "Advanced" card.
- Every toggle's subtitle now names the real environment variable it sets.

### ✨ Added
- **Real startup loading screen.** Replaces the instant screen swap with genuine staged
  progress: native library verification, settings load, storage-access check and
  refresh-rate detection each run as real, awaited steps with a live progress bar —
  not a fixed-duration fake splash. Falls through to the existing repair/diagnostics
  screen automatically if any check actually fails.
- **In-app update checker.** Queries the GitHub Releases API for
  `GoyDevv/IronizedZink` on launch; if a newer version is published, shows a dialog
  with the **full changelog** (release notes, scrollable) and **Update** / **Later**
  buttons. **Update** downloads the release APK in-app via `DownloadManager` (real
  byte-level progress) and hands it straight to the system installer via a
  `FileProvider` URI — no browser required at any point.

---

## v1.0.4 — 2026-07-14

The big usability + performance pass. This is the recommended build.

### ✨ Added
- **Horizontal category preset selector.** Presets are now a swipeable row of
  **uniform-size cards** (name, tagline and a highlight tag) with a detailed panel below,
  instead of a tall vertical list.
- **F3 renderer name.** The debug screen now shows **`IronizedZink | OpenGL <version>`**
  (like MobileGlues) instead of the raw Zink/llvmpipe string. Implemented with Mesa's
  `force_gl_renderer` / `force_gl_vendor` — set purely through the environment, with **no
  changes to the GL loading path**.
- **Real, effective tuning options, grouped by purpose.** Every toggle maps to an
  environment variable the bundled Mesa 23.0.4 Zink driver actually honours:
  - **Performance** — Threaded GL (`mesa_glthread`), Big-core affinity
    (`POJAV_BIG_CORE_AFFINITY`), **Out-of-order drawing** (`allow_draw_out_of_order`, new),
    No-error fast path (`MESA_NO_ERROR`), VSync / uncapped (`FORCE_VSYNC`).
  - **Shaders & compatibility** — OpenGL version (3.3–4.6), Relaxed GLSL
    (`force_glsl_extensions_warn` + `allow_higher_compat_version` +
    `allow_glsl_extension_directive_midshader`), **Expose all extensions**
    (`MESA_EXTENSION_MAX_YEAR`, new), Shader disk cache, Single-file cache
    (`MESA_DISK_CACHE_SINGLE_FILE`).
  - **Experimental** — **Lazy descriptors** (`ZINK_DESCRIPTORS=lazy`, new),
    **Inline uniforms** (`ZINK_INLINE_UNIFORMS`, new), Force software
    (`LIBGL_ALWAYS_SOFTWARE`, last-resort).
- **Environment viewer + one-tap copy** for your launcher's custom-env field.

### 🚀 Changed / performance
- **Potato is now genuinely the fastest preset.** It was capped at **OpenGL 3.3**, which
  forces Sodium down slow paths on modern Minecraft. Potato now uses **OpenGL 4.6** plus
  threaded GL, big-core affinity, no-error, **out-of-order drawing**, **lazy descriptors**
  and an **uncapped** frame rate.
- **Performance** preset also gains out-of-order drawing + no-error while staying
  shader-ready.
- **Preset cards redesigned:** consistent size, richer content, and **flat (no drop
  shadows)** — selection is shown with colour + a border, not elevation.
- **Corrected in-app messaging:** Minecraft **26.x is fully supported** on every preset
  (the old "won't run 26.x" text was wrong).

### 🩹 Fixed
- **Launcher crash on game start (SIGSEGV).** Root cause: the config shim used to *unset*
  environment variables, and removing `FORCE_VSYNC` made the launcher dereference a NULL
  from `getenv("FORCE_VSYNC")`. The shim is now **set-only — it never unsets anything**.
- **Android 15 (16 KB pages):** all bundled native libraries are linked
  **16 KB page-aligned**.
- **Missing C++ runtime:** `libc++_shared.so` is now bundled per-ABI by CI.

### 🗑️ Removed
- **FPS overlay** option — `GALLIUM_HUD` does not draw in this Android Zink build, so it
  was a dead toggle. Gone.
- The short-lived experimental **GL-wrapper library** (`libironized_gl.so`). The F3 name is
  now done via `force_gl_renderer`, so the renderer uses the **proven `libglxshim.so`
  path** with zero added risk.

### ℹ️ Honest scope notes
- Ironized Zink is a **GPU/OpenGL renderer**. **Server tick rate and multiplayer netcode
  are game/server-side** and cannot be changed by a renderer, so there are deliberately
  **no fake "improve tick"/"improve multiplayer" toggles**. (Higher, smoother FPS can still
  make input *feel* more responsive.)
- **FSR / resolution upscaling** is not a Zink environment feature on Android — render
  resolution is controlled by the launcher/OS surface, not the driver — so it isn't
  included as a fake option either.
- Zink translates desktop OpenGL to Vulkan; on some mobile GPUs (e.g. Mali) that carries
  inherent overhead versus a native-GLES renderer, so raw FPS may not always match
  GLES-based options even fully tuned.

---

## v1.0.3 — earlier
- **Fixed the launcher crash** by making the native config shim set-only (never unsets
  environment variables). Confirmed working.

## v1.0.2 — earlier
- Added the **startup verification & self-repair screen**.
- Bundled **`libc++_shared.so`**; **16 KB** page-alignment for the shim.

## v1.0.1 — earlier
- Added the native config **shim** so presets and options **actually apply** in the game
  process (via a config file the launcher loads at startup).

## v1.0.0 — initial
- First public release: the **Zink + Kopper** (Mesa) OpenGL-over-Vulkan renderer packaged
  as a **plugin APK** for the Fold Craft Launcher and ZalithLauncher 2 families, with
  presets, a Material You (dark, dynamic-colour, Lexend) UI, and all four ABIs.
