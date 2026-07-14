/*
 * Ironized Zink — a renderer plugin (by GoyDevv) for Minecraft: Java Edition launchers.
 *
 * The rendering back-end is the Mesa "Zink" Gallium driver (OpenGL-over-Vulkan) with
 * the Kopper WSI. Mesa is MIT-licensed; this integration code is GPL-3.0.
 *
 * Everything here maps to a real environment variable that either the Mesa/Zink driver
 * or the launcher's Pojav bridge honours. The in-app "Save" writes these to a config
 * file that the bundled native shim (libironized_zink.so) applies inside the game
 * process, so changing a preset or option genuinely changes how the game renders.
 */
package com.goydevv.ironizedzink

/** Canonical identity of the Ironized Zink renderer + shared-config location. */
object Zink {
    const val RENDERER_ID = "opengles3_desktopgl_zink_kopper"
    const val GL_NAME = "libglxshim.so"
    const val EGL_NAME = "libEGL_mesa.so"
    const val DRIVER_LIB = "libzink_dri.so"
    const val MESA_VERSION = "23.0.4"

    /** Public folder + file the game process reads (mirrors MobileGlues' /sdcard/MG). */
    const val CONFIG_DIR = "IronizedZink"
    const val CONFIG_FILE = "ironized.env"
}

/** A user-selectable, opinionated configuration profile. */
enum class Preset(
    val displayName: String,
    val tagline: String,
    val mcVersions: String,
    val shaders: String,
    val expect: String,
    val description: String,
    val recommended: Boolean = false,
) {
    POTATO(
        displayName = "Potato",
        tagline = "Absolute maximum FPS",
        mcVersions = "1.8 → latest (incl. 26.x)",
        shaders = "Not recommended",
        expect = "Highest FPS · uncapped · big-core + threaded GL + no-error fast path",
        description = "Everything tuned for raw frame rate: full OpenGL 4.6 (so Sodium's " +
            "fast paths work on modern versions too), threaded GL, big-core affinity, the " +
            "no-error fast path and an uncapped frame rate. Best on weak devices at low " +
            "render distance without shaders.",
    ),
    PERFORMANCE(
        displayName = "Performance",
        tagline = "High FPS with Sodium & light shaders",
        mcVersions = "1.20.x – latest (incl. 26.x)",
        shaders = "Light (e.g. Complementary — low/medium)",
        expect = "High FPS · uncapped · full OpenGL 4.6 for modern versions",
        description = "Full OpenGL 4.6 with threaded GL, big-core affinity, the no-error " +
            "fast path and an uncapped frame rate. Tuned for Sodium/Iris on mid-range and " +
            "flagship devices.",
    ),
    DEFAULT(
        displayName = "Default",
        tagline = "Balanced Zink + shaders",
        mcVersions = "1.16.x – latest (incl. 26.x)",
        shaders = "Full (Iris / OptiFine)",
        expect = "Smooth & stable · VSync on · full OpenGL 4.6 + shader compatibility",
        description = "The recommended profile: OpenGL 4.6 with the compatibility knobs " +
            "shaders rely on, threaded GL and VSync for smooth, tear-free frames. A great " +
            "starting point for most devices.",
        recommended = true,
    ),
    MAX_COMPAT(
        displayName = "Max Compatibility",
        tagline = "Run everything — versions, shaders & mods",
        mcVersions = "All versions (1.8 → latest 26.x)",
        shaders = "Full + heavy shader packs",
        expect = "Correctness over speed · threaded GL off · widest mod/shader support",
        description = "Favours correctness over raw speed: full OpenGL 4.6, relaxed GLSL " +
            "parsing and VSync, with threaded GL disabled (some mods misbehave with it). " +
            "The safest choice when something won't load elsewhere.",
    );
}

/**
 * Tunable options. Each preset seeds sensible defaults; the user may override any of
 * them. Every field maps to a real Mesa/Zink/Pojav environment variable.
 */
data class RenderOptions(
    /** MESA_GL_VERSION_OVERRIDE, e.g. "4.6". */
    val glVersion: String = "4.6",
    /** mesa_glthread — offload GL onto a worker thread (usually higher FPS). */
    val threadedGl: Boolean = true,
    /** POJAV_BIG_CORE_AFFINITY — pin rendering to the performance CPU cores. */
    val bigCoreAffinity: Boolean = false,
    /** FORCE_VSYNC — cap to the display refresh (smooth) vs. uncapped (max FPS). */
    val vsync: Boolean = true,
    /** MESA_NO_ERROR — skip GL error checks (faster, less safe). */
    val noError: Boolean = false,
    /** Keep the on-disk shader cache (faster warm loads). Off => MESA_SHADER_CACHE_DISABLE. */
    val shaderCache: Boolean = true,
    /** MESA_DISK_CACHE_SINGLE_FILE — single-file cache, lower I/O. */
    val singleFileCache: Boolean = true,
    /** Relaxed GLSL parsing for shaders/mods (allow_* + force_glsl_extensions_warn). */
    val relaxGlsl: Boolean = true,
    /** GALLIUM_HUD=fps — on-screen FPS overlay. */
    val showFps: Boolean = false,
    /** LIBGL_ALWAYS_SOFTWARE — CPU fallback (last-resort compatibility / debugging). */
    val forceSoftware: Boolean = false,
) {
    companion object {
        fun forPreset(preset: Preset): RenderOptions = when (preset) {
            Preset.POTATO -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, vsync = false,
                noError = true, shaderCache = true, singleFileCache = true, relaxGlsl = false,
                showFps = false, forceSoftware = false,
            )
            Preset.PERFORMANCE -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, vsync = false,
                noError = true, shaderCache = true, singleFileCache = true, relaxGlsl = true,
                showFps = false, forceSoftware = false,
            )
            Preset.DEFAULT -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = false, vsync = true,
                noError = false, shaderCache = true, singleFileCache = true, relaxGlsl = true,
                showFps = false, forceSoftware = false,
            )
            Preset.MAX_COMPAT -> RenderOptions(
                glVersion = "4.6", threadedGl = false, bigCoreAffinity = false, vsync = true,
                noError = false, shaderCache = true, singleFileCache = false, relaxGlsl = true,
                showFps = false, forceSoftware = false,
            )
        }

        /** True if [options] exactly matches the preset's defaults (no manual tweaks). */
        fun matches(preset: Preset, options: RenderOptions): Boolean = forPreset(preset) == options
    }
}

/**
 * Builds the ordered environment map. The core keys are always present; optional knobs
 * appear only when enabled (the native shim clears them first, so "off" is authoritative).
 */
fun buildEnv(options: RenderOptions): LinkedHashMap<String, String> {
    val env = LinkedHashMap<String, String>()

    // --- Core Ironized Zink selection: Zink + Kopper (overrides the manifest baseline) ---
    env["POJAV_RENDERER"] = Zink.RENDERER_ID
    env["LIBGL_ES"] = "3"
    env["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
    env["MESA_GL_VERSION_OVERRIDE"] = options.glVersion
    env["MESA_GLSL_VERSION_OVERRIDE"] = glslFor(options.glVersion)

    // --- Shader / mod compatibility ---
    // Emitted explicitly (true/false) because the manifest baseline sets these and the
    // runtime shim is set-only (it never unsets), so an explicit value is how "off" wins.
    val relax = if (options.relaxGlsl) "true" else "false"
    env["force_glsl_extensions_warn"] = relax
    env["allow_higher_compat_version"] = relax
    env["allow_glsl_extension_directive_midshader"] = relax

    // --- VSync ---
    // The launcher reads FORCE_VSYNC with strcmp(value, "true"), so use true/false and
    // always set it (never leave it unset — the launcher's check is not null-safe).
    env["FORCE_VSYNC"] = if (options.vsync) "true" else "false"

    // --- Performance / diagnostics (not set by the launcher; presence = enabled) ---
    if (options.threadedGl) env["mesa_glthread"] = "true"
    if (options.bigCoreAffinity) env["POJAV_BIG_CORE_AFFINITY"] = "1"
    if (options.noError) env["MESA_NO_ERROR"] = "1"
    if (options.singleFileCache) env["MESA_DISK_CACHE_SINGLE_FILE"] = "1"
    if (!options.shaderCache) env["MESA_SHADER_CACHE_DISABLE"] = "true"
    if (options.showFps) env["GALLIUM_HUD"] = "fps"
    if (options.forceSoftware) env["LIBGL_ALWAYS_SOFTWARE"] = "1"

    return env
}

private fun glslFor(glVersion: String): String = when (glVersion) {
    "4.6" -> "460"
    "4.5" -> "450"
    "4.3" -> "430"
    "3.3" -> "330"
    else -> "460"
}

/** `KEY=VALUE` (one per line) — the format the native shim parses. */
fun LinkedHashMap<String, String>.toEnvFile(): String =
    entries.joinToString(separator = "\n") { "${it.key}=${it.value}" } + "\n"

/** Colon-separated form for a launcher's custom-environment field. */
fun LinkedHashMap<String, String>.toColonEnv(): String =
    entries.joinToString(separator = ":") { "${it.key}=${it.value}" }
