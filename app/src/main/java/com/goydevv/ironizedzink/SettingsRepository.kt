/*
 * Ironized Zink — settings persistence and config export.
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists the user's renderer choices and exports them to the on-disk config file
 * that the runtime shim reads inside the game process.
 *
 * Persistence uses SharedPreferences (no extra dependencies). The exported config is
 * written to the app-specific external files directory, which needs no runtime
 * permission and is stable across launchers.
 */
object SettingsRepository {
    private const val TAG = "IronizedZink"
    private const val PREFS = "ironized_zink_settings"

    private const val KEY_PRESET = "preset"
    private const val KEY_GL_VERSION = "gl_version"
    private const val KEY_THREADED_GL = "threaded_gl"
    private const val KEY_SHOW_FPS = "show_fps"
    private const val KEY_SINGLE_CACHE = "single_file_cache"
    private const val KEY_NO_ERROR = "no_error"
    private const val KEY_RELAX_GLSL = "relax_glsl"
    private const val KEY_ALL_EXT = "all_extensions"
    private const val KEY_FORCE_SW = "force_software"

    fun loadPreset(context: Context): Preset {
        val name = prefs(context).getString(KEY_PRESET, Preset.DEFAULT.name) ?: Preset.DEFAULT.name
        return runCatching { Preset.valueOf(name) }.getOrDefault(Preset.DEFAULT)
    }

    fun loadOptions(context: Context): RenderOptions {
        val p = prefs(context)
        // Seed from the current preset's defaults, then apply any stored overrides.
        val base = RenderOptions.forPreset(loadPreset(context))
        if (!p.contains(KEY_GL_VERSION)) return base
        return RenderOptions(
            glVersion = p.getString(KEY_GL_VERSION, base.glVersion) ?: base.glVersion,
            threadedGl = p.getBoolean(KEY_THREADED_GL, base.threadedGl),
            showFps = p.getBoolean(KEY_SHOW_FPS, base.showFps),
            singleFileCache = p.getBoolean(KEY_SINGLE_CACHE, base.singleFileCache),
            noError = p.getBoolean(KEY_NO_ERROR, base.noError),
            relaxGlsl = p.getBoolean(KEY_RELAX_GLSL, base.relaxGlsl),
            allExtensions = p.getBoolean(KEY_ALL_EXT, base.allExtensions),
            forceSoftware = p.getBoolean(KEY_FORCE_SW, base.forceSoftware),
        )
    }

    fun save(context: Context, preset: Preset, options: RenderOptions) {
        prefs(context).edit().apply {
            putString(KEY_PRESET, preset.name)
            putString(KEY_GL_VERSION, options.glVersion)
            putBoolean(KEY_THREADED_GL, options.threadedGl)
            putBoolean(KEY_SHOW_FPS, options.showFps)
            putBoolean(KEY_SINGLE_CACHE, options.singleFileCache)
            putBoolean(KEY_NO_ERROR, options.noError)
            putBoolean(KEY_RELAX_GLSL, options.relaxGlsl)
            putBoolean(KEY_ALL_EXT, options.allExtensions)
            putBoolean(KEY_FORCE_SW, options.forceSoftware)
            apply()
        }
    }

    /**
     * Writes the computed env to the config file the runtime shim consumes.
     * Returns the absolute path on success, or null on failure.
     */
    fun exportConfig(context: Context, options: RenderOptions): String? = runCatching {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, Zink.CONFIG_FILE)
        file.writeText(buildEnv(options).toEnvFile())
        Log.i(TAG, "Wrote renderer config to ${file.absolutePath}")
        file.absolutePath
    }.onFailure { Log.w(TAG, "Failed to write renderer config", it) }.getOrNull()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
