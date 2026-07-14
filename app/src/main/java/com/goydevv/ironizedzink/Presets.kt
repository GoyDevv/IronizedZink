/*
 * Ironized Zink — a Kopper Zink renderer plugin for Minecraft: Java Edition launchers.
 *
 * The rendering back-end is the Mesa "Zink" Gallium driver (OpenGL-over-Vulkan) with
 * the Kopper WSI. Mesa is licensed under the MIT license (see assets/licenses).
 * This launcher-integration code is licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

/**
 * Canonical identity of the Kopper Zink renderer as consumed by the launcher.
 *
 * These values MUST stay consistent with the `<meta-data>` in AndroidManifest.xml,
 * because the launcher reads the manifest — this object mirrors them so the in-app
 * UI can present and export the exact same configuration.
 */
object Zink {
    /** POJAV_RENDERER id understood by the Pojav/FCL/Zalith native bridge. */
    const val RENDERER_ID = "opengles3_desktopgl_zink_kopper"

    /** GL entry library (glName) the launcher dlopen()s from nativeLibraryDir. */
    const val GL_NAME = "libglxshim.so"

    /** EGL library (eglName) the launcher dlopen()s from nativeLibraryDir. */
    const val EGL_NAME = "libEGL_mesa.so"

    /** The Gallium DRI driver discovered via MESA_LOADER_DRIVER_OVERRIDE=zink. */
    const val DRIVER_LIB = "libzink_dri.so"

    /** File name the settings screen writes and the runtime shim reads. */
    const val CONFIG_FILE = "ironized.env"

    /** Mesa version the bundled Zink driver was built from. */
    const val MESA_VERSION = "23.0.4"
}

/** A user-selectable, opinionated configuration profile. */
enum class Preset(
    val displayName: String,
    val tagline: String,
    val mcVersions: String,
    val shaders: String,
    val description: String,
) {
    POTATO(
        displayName = "Potato",
        tagline = "Maximum FPS on weak devices",
        mcVersions = "1.8.9 – 1.20.x (vanilla)",
        shaders = "Not recommended",
        description = "Strips everything non-essential and uncaps the frame rate. " +
            "Best for low-RAM / low-end GPUs running vanilla or lightweight mod packs.",
    ),
    PERFORMANCE(
        displayName = "Performance",
        tagline = "High FPS with Sodium & light shaders",
        mcVersions = "1.20.x – latest (incl. 26.x)",
        shaders = "Light (e.g. Complementary — low)",
        description = "Threaded GL, no-error fast path and a single-file shader cache. " +
            "Tuned for Sodium/Iris on mid-range and flagship devices.",
    ),
    DEFAULT(
        displayName = "Default",
        tagline = "Balanced Zink + shaders (recommended)",
        mcVersions = "1.16.x – latest (incl. 26.x)",
        shaders = "Full (Iris / OptiFine)",
        description = "The proven Kopper Zink profile: desktop OpenGL 4.6 over Vulkan " +
            "with the compatibility knobs shaders rely on. A great starting point.",
    ),
    MAX_COMPAT(
        displayName = "Max Compatibility",
        tagline = "Run everything — versions, shaders & mods",
        mcVersions = "All versions (1.8 → latest 26.x)",
        shaders = "Full + heavy shader packs",
        description = "Favours correctness over speed: exposes all GL extensions and " +
            "relaxes GLSL parsing so the widest range of mods and shader packs load.",
    );
}

/**
 * Tunable options. Each preset seeds sensible defaults; the user may override any of
 * them in the "Advanced" section. Everything here maps to a real Mesa/Zink/Gallium
 * environment variable that the bundled driver honours.
 */
data class RenderOptions(
    /** MESA_GL_VERSION_OVERRIDE, e.g. "4.6". */
    val glVersion: String = "4.6",
    /** mesa_glthread — offload GL onto a worker thread (usually higher FPS). */
    val threadedGl: Boolean = true,
    /** GALLIUM_HUD=fps — draw an on-screen FPS overlay. */
    val showFps: Boolean = false,
    /** MESA_DISK_CACHE_SINGLE_FILE — faster, lower-IO shader cache. */
    val singleFileCache: Boolean = true,
    /** MESA_NO_ERROR — skip GL error checks (faster, less safe). */
    val noError: Boolean = false,
    /** Relaxed GLSL parsing for shaders/mods (allow_* + force_glsl_extensions_warn). */
    val relaxGlsl: Boolean = true,
    /** Expose every GL extension regardless of age (clears MESA_EXTENSION_MAX_YEAR). */
    val allExtensions: Boolean = true,
    /** LIBGL_ALWAYS_SOFTWARE — CPU fallback (last-resort compatibility / debugging). */
    val forceSoftware: Boolean = false,
) {
    companion object {
        fun forPreset(preset: Preset): RenderOptions = when (preset) {
            Preset.POTATO -> RenderOptions(
                glVersion = "4.6",
                threadedGl = true,
                showFps = false,
                singleFileCache = true,
                noError = true,
                relaxGlsl = false,
                allExtensions = false,
                forceSoftware = false,
            )
            Preset.PERFORMANCE -> RenderOptions(
                glVersion = "4.6",
                threadedGl = true,
                showFps = false,
                singleFileCache = true,
                noError = true,
                relaxGlsl = true,
                allExtensions = false,
                forceSoftware = false,
            )
            Preset.DEFAULT -> RenderOptions(
                glVersion = "4.6",
                threadedGl = true,
                showFps = false,
                singleFileCache = true,
                noError = false,
                relaxGlsl = true,
                allExtensions = true,
                forceSoftware = false,
            )
            Preset.MAX_COMPAT -> RenderOptions(
                glVersion = "4.6",
                threadedGl = false,
                showFps = false,
                singleFileCache = true,
                noError = false,
                relaxGlsl = true,
                allExtensions = true,
                forceSoftware = false,
            )
        }
    }
}

/**
 * Builds the ordered environment map for the given options. This is the single source
 * of truth used both for the on-screen preview and for the exported config file.
 */
fun buildEnv(options: RenderOptions): LinkedHashMap<String, String> {
    val env = LinkedHashMap<String, String>()

    // --- Core Kopper Zink selection (must match the manifest baseline) ---
    env["POJAV_RENDERER"] = Zink.RENDERER_ID
    env["LIBGL_ES"] = "3"
    env["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"

    // --- Desktop GL version presented to Minecraft ---
    env["MESA_GL_VERSION_OVERRIDE"] = options.glVersion
    env["MESA_GLSL_VERSION_OVERRIDE"] = glslFor(options.glVersion)

    // --- Shader / mod compatibility ---
    if (options.relaxGlsl) {
        env["force_glsl_extensions_warn"] = "true"
        env["allow_higher_compat_version"] = "true"
        env["allow_glsl_extension_directive_midshader"] = "true"
    }
    if (options.allExtensions) {
        // Do NOT cap extensions by year — expose everything the driver supports.
        env["MESA_EXTENSION_MAX_YEAR"] = "0"
    }

    // --- Performance ---
    if (options.threadedGl) env["mesa_glthread"] = "true"
    if (options.noError) env["MESA_NO_ERROR"] = "1"
    if (options.singleFileCache) env["MESA_DISK_CACHE_SINGLE_FILE"] = "1"

    // --- Diagnostics / fallback ---
    if (options.showFps) env["GALLIUM_HUD"] = "fps"
    if (options.forceSoftware) env["LIBGL_ALWAYS_SOFTWARE"] = "1"

    return env
}

/** Maps a GL version to a matching GLSL version string. */
private fun glslFor(glVersion: String): String = when (glVersion) {
    "4.6" -> "460"
    "4.5" -> "450"
    "4.3" -> "430"
    "3.3" -> "330"
    else -> "460"
}

/** Serialises the env map to the `KEY=VALUE` (one per line) format the shim reads. */
fun LinkedHashMap<String, String>.toEnvFile(): String =
    entries.joinToString(separator = "\n") { "${it.key}=${it.value}" } + "\n"

/** Serialises the env map to the colon-separated form launchers expect in a custom-env box. */
fun LinkedHashMap<String, String>.toColonEnv(): String =
    entries.joinToString(separator = ":") { "${it.key}=${it.value}" }
