/*
 * Ironized Zink — settings persistence and config export.
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Persists the user's choices (SharedPreferences) and exports them to the shared
 * config file the native shim reads inside the game process.
 *
 * The shim runs as a *different app* (the launcher), so the file must live in public
 * shared storage — writing there needs All-files access on Android 11+ (mirrors how
 * MobileGlues uses /sdcard/MG). We also drop a copy in the app-specific dir for
 * debugging.
 */
object SettingsRepository {
    private const val TAG = "IronizedZink"
    private const val PREFS = "ironized_zink_settings"

    private const val KEY_PRESET = "preset"
    private const val KEY_GL = "gl_version"
    private const val KEY_THREADED = "threaded_gl"
    private const val KEY_BIGCORE = "big_core"
    private const val KEY_VSYNC = "vsync"
    private const val KEY_NOERR = "no_error"
    private const val KEY_CACHE = "shader_cache"
    private const val KEY_SINGLE = "single_file_cache"
    private const val KEY_RELAX = "relax_glsl"
    private const val KEY_FPS = "show_fps"
    private const val KEY_SW = "force_software"

    fun loadPreset(context: Context): Preset =
        runCatching { Preset.valueOf(prefs(context).getString(KEY_PRESET, Preset.DEFAULT.name)!!) }
            .getOrDefault(Preset.DEFAULT)

    fun loadOptions(context: Context): RenderOptions {
        val p = prefs(context)
        val base = RenderOptions.forPreset(loadPreset(context))
        if (!p.contains(KEY_GL)) return base
        return RenderOptions(
            glVersion = p.getString(KEY_GL, base.glVersion) ?: base.glVersion,
            threadedGl = p.getBoolean(KEY_THREADED, base.threadedGl),
            bigCoreAffinity = p.getBoolean(KEY_BIGCORE, base.bigCoreAffinity),
            vsync = p.getBoolean(KEY_VSYNC, base.vsync),
            noError = p.getBoolean(KEY_NOERR, base.noError),
            shaderCache = p.getBoolean(KEY_CACHE, base.shaderCache),
            singleFileCache = p.getBoolean(KEY_SINGLE, base.singleFileCache),
            relaxGlsl = p.getBoolean(KEY_RELAX, base.relaxGlsl),
            showFps = p.getBoolean(KEY_FPS, base.showFps),
            forceSoftware = p.getBoolean(KEY_SW, base.forceSoftware),
        )
    }

    fun save(context: Context, preset: Preset, o: RenderOptions) {
        prefs(context).edit().apply {
            putString(KEY_PRESET, preset.name)
            putString(KEY_GL, o.glVersion)
            putBoolean(KEY_THREADED, o.threadedGl)
            putBoolean(KEY_BIGCORE, o.bigCoreAffinity)
            putBoolean(KEY_VSYNC, o.vsync)
            putBoolean(KEY_NOERR, o.noError)
            putBoolean(KEY_CACHE, o.shaderCache)
            putBoolean(KEY_SINGLE, o.singleFileCache)
            putBoolean(KEY_RELAX, o.relaxGlsl)
            putBoolean(KEY_FPS, o.showFps)
            putBoolean(KEY_SW, o.forceSoftware)
            apply()
        }
    }

    /** True when we can write to public shared storage (so the shim can read it). */
    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /** The public path the native shim reads first. */
    fun sharedConfigFile(): File =
        File(Environment.getExternalStorageDirectory(), "${Zink.CONFIG_DIR}/${Zink.CONFIG_FILE}")

    /**
     * Writes the computed env to the shared config file (and an app-local copy).
     * Returns true if the shared (shim-readable) file was written.
     */
    fun exportConfig(context: Context, options: RenderOptions): Boolean {
        val contents = buildEnv(options).toEnvFile()

        // App-local copy (debugging; not readable by the launcher on Android 11+).
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, Zink.CONFIG_FILE).writeText(contents)
        }.onFailure { Log.w(TAG, "local config write failed", it) }

        // Shared copy the game process actually reads.
        return runCatching {
            val f = sharedConfigFile()
            f.parentFile?.mkdirs()
            f.writeText(contents)
            Log.i(TAG, "wrote shared config: ${f.absolutePath}")
            true
        }.onFailure { Log.w(TAG, "shared config write failed", it) }.getOrDefault(false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
