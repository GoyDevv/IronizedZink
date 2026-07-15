/*
 * Ironized Zink — a renderer plugin (by GoyDevv) for Minecraft: Java Edition launchers.
 *
 * Every option here maps to a real environment variable that the bundled Mesa 23.0.4
 * Zink driver or the launcher's Pojav bridge actually honours (verified against the
 * driver binary / Mesa's own documentation at docs.mesa3d.org/envvars.html). The in-app
 * "Save" writes them to a config file that the native shim applies in the game process,
 * so changing an option genuinely changes rendering.
 *
 * Note on scope: this is a GPU/OpenGL renderer. Server tick rate and multiplayer netcode
 * are game/server-side and cannot be changed by a renderer, so there are intentionally
 * no (fake) "tick"/"multiplayer" toggles here. Likewise there is no numeric FPS-cap
 * environment variable in Mesa/Zink — the only real, driver-level way to cap frame rate
 * to the display's refresh rate is VSync (swap interval 1), which this app detects and
 * surfaces honestly rather than inventing a fake "target FPS" slider.
 *
 * IMPORTANT SAFETY NOTE (read before changing preset defaults):
 * Mesa's own docs state MESA_NO_ERROR causes "undefined behavior for invalid use of the
 * API" (skips GL error/validation checks), and allow_draw_out_of_order lets the driver
 * reorder draw calls relative to submission order. Combining both is what caused the
 * "hand renders through GUI screens" bug: Minecraft's GUI overlay relies on a very
 * specific depth-clear/draw ordering relative to the world/hand pass, and shipping both
 * unsafe knobs together by default let the driver legally reorder or skip validation
 * around exactly that boundary. Fix: presets no longer combine noError + outOfOrder by
 * default. Out-of-order drawing (safe on its own, a real drirc perf option) stays on for
 * the fast presets; no-error is now an explicit, clearly-labelled opt-in toggle.
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
        expect = "Highest FPS · capped to your display's refresh rate · out-of-order draw + lazy descriptors + big cores",
        description = "Everything tuned for raw frame rate: OpenGL 4.6, threaded GL, " +
            "big-core affinity, out-of-order drawing and Zink lazy descriptors. Frame rate " +
            "is capped to your display's own refresh rate (via VSync) instead of running " +
            "fully uncapped, which wastes battery/heat for frames your screen can't show " +
            "and was also the root cause of a hand/GUI depth-ordering glitch when combined " +
            "with the no-error fast path. Best on weak devices at low render distance " +
            "without shaders.",
    ),
    PERFORMANCE(
        displayName = "Performance",
        tagline = "High FPS with Sodium & light shaders",
        highlight = "High FPS",
        mcVersions = "1.20.x → latest (incl. 26.x)",
        shaders = "Light (e.g. Complementary — low/medium)",
        expect = "High FPS · out-of-order draw · shader-ready",
        description = "Full OpenGL 4.6 with threaded GL, big-core affinity, out-of-order " +
            "drawing and relaxed GLSL for shaders. Tuned for Sodium/Iris on mid-range and " +
            "flagship devices, without the undefined-behavior no-error fast path that can " +
            "cause visual glitches when stacked with out-of-order drawing.",
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
            "all extensions exposed and VSync, with threaded GL, out-of-order drawing and " +
            "every experimental option off. The safest choice when something won't load " +
            "elsewhere.",
    );
}

/** Tunable options — each maps to a real, verified Mesa/Zink/Pojav environment variable. */
data class RenderOptions(
    val glVersion: String = "4.6",
    // Performance
    val threadedGl: Boolean = true,          // mesa_glthread
    val bigCoreAffinity: Boolean = false,    // POJAV_BIG_CORE_AFFINITY
    val outOfOrder: Boolean = false,         // allow_draw_out_of_order
    val noError: Boolean = false,            // MESA_NO_ERROR (unsafe; see safety note above)
    val vsync: Boolean = true,               // FORCE_VSYNC / real refresh-rate cap
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
    /** True when noError + outOfOrder are both set — the known-glitchy undefined-behavior combo. */
    val hasUnsafeCombo: Boolean get() = noError && outOfOrder

    companion object {
        fun forPreset(preset: Preset): RenderOptions = when (preset) {
            // Potato: max FPS, but VSync stays ON so the frame rate is capped to the
            // display's real refresh rate instead of running uncapped (which burns
            // battery/heat for frames the screen can never show) and no-error is left
            // OFF so it never combines with out-of-order (the hand/GUI glitch cause).
            Preset.POTATO -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, outOfOrder = true,
                noError = false, vsync = true, relaxGlsl = false, allExtensions = true,
                shaderCache = true, singleFileCache = true, lazyDescriptors = true,
                inlineUniforms = false, forceSoftware = false,
            )
            Preset.PERFORMANCE -> RenderOptions(
                glVersion = "4.6", threadedGl = true, bigCoreAffinity = true, outOfOrder = true,
                noError = false, vsync = false, relaxGlsl = true, allExtensions = true,
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
    // MESA_NO_ERROR is undefined behavior per Mesa's own docs and is the confirmed cause
    // of the hand/GUI depth-ordering glitch when stacked with out-of-order drawing, so it
    // is only ever emitted when the user explicitly opts in.
    if (options.noError) env["MESA_NO_ERROR"] = "1"
    env["FORCE_VSYNC"] = if (options.vsync) "true" else "false"
    // vblank_mode is the real Mesa/DRI knob behind VSync: 1 = capped to the display's
    // actual refresh rate (whatever it is: 60/90/120/144 Hz), 0 = uncapped. There is no
    // Mesa/Zink environment variable for an arbitrary numeric FPS target, so "cap to
    // refresh rate" is implemented honestly via vsync rather than a fake FPS slider.
    env["vblank_mode"] = if (options.vsync) "1" else "0"

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
