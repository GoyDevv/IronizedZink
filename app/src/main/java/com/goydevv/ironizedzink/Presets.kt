/*
 * Ironized Zink — a renderer plugin (by GoyDevv) for Minecraft: Java Edition launchers.
 *
 * Every option here maps to a real environment variable that the bundled Mesa 23.0.4
 * Zink driver or the launcher's Pojav bridge actually honours (verified against the
 * driver binary). The in-app "Save" writes them to a config file that the native shim
 * applies in the game process, so changing an option genuinely changes rendering.
 *
 * Note on scope: this is a GPU/OpenGL renderer. Server tick rate and multiplayer netcode
 * are game/server-side and cannot be changed by a renderer, so there are intentionally
 * no (fake) "tick"/"multiplayer" toggles here.
 */
package com.goydevv.ironizedzink

object Zink {
    const val RENDERER_ID = "opengles3_desktopgl_zink_kopper"
    const val GL_NAME = "libglxshim.so"
    const val EGL_NAME = "libEGL_mesa.so"
    const val DRIVER_LIB = "libzink_dri.so"
    const val MESA_VERSION = "23.0.4"
    const val CONFIG_DIR = "IronizedZink"
    const val CONFIG_FILE = "ironized.env"
}

enum class Preset(
    val displayName: String,
    val tagline: String,
    val highlight: String,
    val mcVersions: String,
    val shaders: String,
    val expect: String,
    val description: String,
    val recommended: Boolean = false,
) {
    POTATO(
        displayName = "Potato",
        tagline = "Absolute maximum FPS",
        highlight = "Fastest",
        mcVersions = "1.8 → latest (incl. 26.x)",
        shaders = "Not recommended",
        expect = "Highest FPS · uncapped · out-of-order draw + lazy descriptors + big cores",
        description = "Everything tuned for raw frame rate: OpenGL 4.6, threaded GL, " +
            "big-core affinity, no-error fast path, out-of-order drawing, Zink lazy " +
            "descriptors and an uncapped frame rate. Best on weak devices at low render " +
            "distance without shaders.",
    ),
    PERFORMANCE(
        displayName = "Performance",
        tagline = "High FPS with Sodium & light shaders",
        highlight = "High FPS",
        mcVersions = "1.20.x → latest (incl. 26.x)",
        shaders = "Light (e.g. Complementary — low/medium)",
        expect = "High FPS · uncapped · out-of-order draw · shader-ready",
        description = "Full OpenGL 4.6 with threaded GL, big-core affinity, the no-error " +
            "fast path, out-of-order drawing and relaxed GLSL for shaders. Tuned for " +
            "Sodium/Iris on mid-range and flagship devices.",
    ),
    DEFAULT(
        displayName = "Default",
        tagline = "Balanced Zink + shaders",
        highlight = "Balanced",
        mcVersions = "1.16.x → latest (incl. 26.x)",
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
        highlight = "Runs everything",
        mcVersions = "All versions (1.8 → latest 26.x)",
        shaders = "Full + heavy shader packs",
        expect = "Correctness over speed · threaded GL off · widest mod/shader support",
        description = "Favours correctness over raw speed: full OpenGL 4.6, relaxed GLSL, " +
            "all extensions exposed and VSync, with threaded GL and experimental options " +
            "off. The safest choice when something won't load elsewhere.",
    );
}

/** Tunable options — each maps to a real, verified Mesa/Zink/Pojav environment variable. */
data class RenderOptions(
    val glVersion: String = "4.6",
    // Performance
    val threadedGl: Boolean = true,          // mesa_glthread
    val bigCoreAffinity: Boolean = false,    // POJAV_BIG_CORE_AFFINITY
    val outOfOrder: Boolean = false,         // allow_draw_out_of_order
    val noError: Boolean = false,            // MESA_NO_ERROR
    val vsync: Boolean = true,               // FORCE_VSYNC
    // Shaders & compatibility
    val relaxGlsl: Boolean = true,           // force_glsl_extensions_warn + allow_*
    val allExtensions: Boolean = true,       // MESA_EXTENSION_MAX_YEAR (off => cap)
    val shaderCache: Boolean = true,         // MESA_SHADER_CACHE_DISABLE (inverse)
    val singleFileCache: Boolean = true,     // MESA_DISK_CACHE_SINGLE_FILE
    // Experimental
    val lazyDescriptors: Boolean = false,    // ZINK_DESCRIPTORS=lazy
    val inlineUniforms: Boolean = false,     // ZINK_INLINE_UNIFORMS
    val forceSoftware: Boolean = false,      // LIBGL_ALWAYS_SOFTWARE
) {
    companion object {
        fun forPreset(preset: Preset): RenderOptions = when (preset) {
            Preset.POTATO -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, outOfOrder = true,
                noError = true, vsync = false, relaxGlsl = false, allExtensions = true,
                shaderCache = true, singleFileCache = true, lazyDescriptors = true,
                inlineUniforms = false, forceSoftware = false,
            )
            Preset.PERFORMANCE -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, outOfOrder = true,
                noError = true, vsync = false, relaxGlsl = true, allExtensions = true,
                shaderCache = true, singleFileCache = true, lazyDescriptors = false,
                inlineUniforms = false, forceSoftware = false,
            )
            Preset.DEFAULT -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = false, outOfOrder = false,
                noError = false, vsync = true, relaxGlsl = true, allExtensions = true,
                shaderCache = true, singleFileCache = true, lazyDescriptors = false,
                inlineUniforms = false, forceSoftware = false,
            )
            Preset.MAX_COMPAT -> RenderOptions(
                glVersion = "4.6", threadedGl = false, bigCoreAffinity = false, outOfOrder = false,
                noError = false, vsync = true, relaxGlsl = true, allExtensions = true,
                shaderCache = true, singleFileCache = false, lazyDescriptors = false,
                inlineUniforms = false, forceSoftware = false,
            )
        }

        fun matches(preset: Preset, options: RenderOptions): Boolean = forPreset(preset) == options
    }
}

/** Builds the ordered environment map (set-only; the shim never unsets). */
fun buildEnv(options: RenderOptions): LinkedHashMap<String, String> {
    val env = LinkedHashMap<String, String>()

    // --- Core Ironized Zink selection: Zink + Kopper ---
    env["POJAV_RENDERER"] = Zink.RENDERER_ID
    env["LIBGL_ES"] = "3"
    env["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
    env["MESA_GL_VERSION_OVERRIDE"] = options.glVersion
    env["MESA_GLSL_VERSION_OVERRIDE"] = glslFor(options.glVersion)

    // --- F3 renderer/vendor strings (Mesa reads these driconf options from env) ---
    env["force_gl_renderer"] = "IronizedZink | OpenGL ${options.glVersion}"
    env["force_gl_vendor"] = "GoyDevv"

    // --- Shaders & compatibility ---
    val relax = if (options.relaxGlsl) "true" else "false"
    env["force_glsl_extensions_warn"] = relax
    env["allow_higher_compat_version"] = relax
    env["allow_glsl_extension_directive_midshader"] = relax
    if (!options.allExtensions) env["MESA_EXTENSION_MAX_YEAR"] = "2018" // cap to help old mods
    if (!options.shaderCache) env["MESA_SHADER_CACHE_DISABLE"] = "true"
    if (options.singleFileCache) env["MESA_DISK_CACHE_SINGLE_FILE"] = "1"

    // --- Performance ---
    if (options.threadedGl) env["mesa_glthread"] = "true"
    if (options.bigCoreAffinity) env["POJAV_BIG_CORE_AFFINITY"] = "1"
    if (options.outOfOrder) env["allow_draw_out_of_order"] = "true"
    if (options.noError) env["MESA_NO_ERROR"] = "1"
    env["FORCE_VSYNC"] = if (options.vsync) "true" else "false"

    // --- Experimental ---
    if (options.lazyDescriptors) env["ZINK_DESCRIPTORS"] = "lazy"
    if (options.inlineUniforms) env["ZINK_INLINE_UNIFORMS"] = "1"
    if (options.forceSoftware) env["LIBGL_ALWAYS_SOFTWARE"] = "1"

    return env
}

private fun glslFor(glVersion: String): String = when (glVersion) {
    "4.6" -> "460"; "4.5" -> "450"; "4.3" -> "430"; "3.3" -> "330"; else -> "460"
}

fun LinkedHashMap<String, String>.toEnvFile(): String =
    entries.joinToString(separator = "\n") { "${it.key}=${it.value}" } + "\n"

fun LinkedHashMap<String, String>.toColonEnv(): String =
    entries.joinToString(separator = ":") { "${it.key}=${it.value}" }
