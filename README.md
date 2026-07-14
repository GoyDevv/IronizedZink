<div align="center">

# Ironized Zink

**A Kopper Zink renderer plugin for Minecraft: Java Edition on Android.**

Desktop **OpenGL 4.6 over Vulkan**, delivered as a drop-in renderer for
**ZalithLauncher 2** and the **Fold Craft Launcher** family — with built-in
performance presets and a full tuning UI.

[![Build](https://github.com/GoyDevv/IronizedZink/actions/workflows/build.yml/badge.svg)](https://github.com/GoyDevv/IronizedZink/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Renderer: Zink + Kopper](https://img.shields.io/badge/renderer-Zink%20%2B%20Kopper-8FB3D9)](https://docs.mesa3d.org/drivers/zink.html)

</div>

---

## What is it?

Ironized Zink packages the **Zink** Gallium driver from the **Mesa 3-D Graphics
Library** — the mature OpenGL-on-Vulkan translator — together with the **Kopper**
window-system layer, and exposes it as a standard **renderer plugin APK**.

Install the APK, then simply pick **“Ironized Zink”** from your launcher’s renderer
list. Because Zink renders desktop OpenGL through your device’s **Vulkan** driver, it
is well suited to modern Minecraft — including the Vulkan-era **1.21.x and 26.x**
releases — as well as **Sodium/Iris** and shader packs.

> Ironized Zink is a repackaging + configuration project. All rendering is done by
> Mesa/Zink/Kopper — see **[CREDITS.md](CREDITS.md)**.

## Features

- ⚡ **OpenGL 4.6 (GLSL 460)** presented to Minecraft, translated to Vulkan by Zink.
- 🧩 **Plugin, not a fork** — works alongside your launcher’s other renderers.
- 🎚️ **Four one-tap presets** — Potato, Performance, Default, Max Compatibility.
- 🔧 **Full advanced tuning** — GL version, threaded GL, FPS overlay, shader cache,
  no-error fast path, relaxed GLSL, extension exposure, software fallback.
- 📤 **Env export** — copy the exact environment for your launcher’s custom-env field.
- 📦 **All 4 ABIs** — `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- 🪶 **Modern UI** — Jetpack Compose + Material 3.

## Compatibility

| | |
|---|---|
| **Launchers** | ZalithLauncher 2 family (`zalithRendererPlugin`), Fold Craft Launcher family (`fclPlugin`) |
| **Minecraft** | 1.8 → latest, including the Vulkan-era **26.x** (via desktop-GL over Vulkan) |
| **Android**   | 8.0+ (API 26), 64-bit and 32-bit |
| **GPU**       | Any device with a working **Vulkan 1.1+** driver (Adreno, Mali, Xclipse, …) |

## Presets

| Preset | Best for | Minecraft | Shaders |
|--------|----------|-----------|---------|
| **Potato** | Max FPS on weak devices | 1.8.9 – 1.20.x (vanilla) | Not recommended |
| **Performance** | High FPS with Sodium | 1.20.x – latest (incl. 26.x) | Light |
| **Default** | Balanced Zink + shaders *(recommended)* | 1.16.x – latest (incl. 26.x) | Full (Iris/OptiFine) |
| **Max Compatibility** | Run everything | All versions (1.8 → 26.x) | Full + heavy packs |

Each preset maps to a concrete set of Mesa/Zink environment variables; open the app to
see and fine-tune them.

## Installation

1. Download `Ironized-Zink.apk` from the [Releases](https://github.com/GoyDevv/IronizedZink/releases) page.
2. Install it (you may need to allow “install from unknown sources”).
3. Open your launcher → **Settings → Renderer** and select **Ironized Zink**.
4. *(Optional)* Open the **Ironized Zink** app to choose a preset or fine-tune settings.

## How it works

The launcher discovers renderer plugins by scanning installed apps for specific
`<meta-data>` in their manifest. Ironized Zink declares:

```xml
<meta-data android:name="fclPlugin" android:value="true"/>
<meta-data android:name="zalithRendererPlugin" android:value="true"/>
<meta-data android:name="renderer" android:value="Ironized Zink:libglxshim.so:libEGL_mesa.so"/>
<meta-data android:name="pojavEnv"  android:value="POJAV_RENDERER=opengles3_desktopgl_zink_kopper:…"/>
```

At launch the launcher loads the plugin’s native libraries from its `nativeLibraryDir`
and injects the declared environment. Mesa then loads `libzink_dri.so` via
`MESA_LOADER_DRIVER_OVERRIDE=zink` and renders OpenGL through Vulkan.

## Building

Builds run entirely on **GitHub Actions** (see [`.github/workflows/build.yml`](.github/workflows/build.yml)) —
no local Android SDK required. To build the debug APK:

```bash
./gradlew assembleDebug
```

Requirements: JDK 21, Android SDK (compileSdk 36). The output APK is uploaded as a CI
artifact on every push.

## Credits

Zink, Kopper and the entire renderer come from the **Mesa 3-D Graphics Library** (MIT).
The plugin interface comes from **Fold Craft Launcher** / **ZalithLauncher 2** (GPL-3.0).
Full attribution and licenses: **[CREDITS.md](CREDITS.md)**.

## License

Ironized Zink’s integration code is licensed under the **GNU General Public License
v3.0** — see [LICENSE](LICENSE). Bundled Mesa/Zink binaries are MIT-licensed; see
`app/src/main/assets/licenses/`.

*Not affiliated with Mojang, the Mesa project, the FCL team, or the ZalithLauncher team.
Minecraft is a trademark of Mojang Synergies AB.*
