/*
 * Ironized Zink — native library verification & self-repair.
 *
 * The renderer depends on native .so shipped in this APK. On some devices/ROMs the
 * installer does not extract them to the app's nativeLibraryDir, or they fail to load.
 * This module verifies each library (exists + actually loads) and captures the precise
 * loader error, and can re-extract the libraries straight out of our own base.apk to
 * shared storage so the exact failure can be diagnosed and worked around.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object NativeLibs {
    private const val TAG = "IronizedZink"

    /** All libraries the renderer ships, in dependency order. */
    val FILES = listOf(
        "libc++_shared.so",
        "libcutils.so",
        "libglapi.so",
        "libEGL_mesa.so",
        "libglxshim.so",
        "libironized_gl.so",
        "libzink_dri.so",
        "libironized_zink.so",
    )

    /** Libraries safe to actually dlopen in this (non-GL) process for a load test.
     *  libglxshim.so is self-contained (system deps only) and is the exact library the
     *  launcher loads for GL, so testing it is decisive. libEGL_mesa/libzink_dri are
     *  Mesa components that initialise WSI/Vulkan lazily, so we only check they exist. */
    private val LOAD_TESTABLE = setOf(
        "libcutils.so", "libglapi.so", "libglxshim.so", "libironized_zink.so",
    )

    data class LibStatus(
        val file: String,
        val exists: Boolean,
        val sizeBytes: Long,
        val loadTested: Boolean,
        val loaded: Boolean,
        val error: String?,
    ) {
        val ok: Boolean get() = exists && (!loadTested || loaded)
    }

    fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    fun nativeLibDir(context: Context): String = context.applicationInfo.nativeLibraryDir

    /** Verify every library at the app's nativeLibraryDir. */
    fun verify(context: Context): List<LibStatus> {
        val dir = nativeLibDir(context)
        return FILES.map { name -> checkOne(File(dir, name)) }
    }

    /** True if the critical GL entry lib both exists and loads. */
    fun quickOk(context: Context): Boolean {
        val f = File(nativeLibDir(context), "libglxshim.so")
        if (!f.exists()) return false
        return try { System.load(f.absolutePath); true } catch (t: Throwable) { false }
    }

    private fun checkOne(f: File): LibStatus {
        val exists = f.exists()
        val size = if (exists) f.length() else 0L
        val testable = LOAD_TESTABLE.contains(f.name)
        var loaded = false
        var error: String? = if (!exists) "missing at ${f.parent}" else null
        if (exists && testable) {
            try {
                System.load(f.absolutePath)
                loaded = true
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
            }
        }
        return LibStatus(f.name, exists, size, testable, loaded, error)
    }

    /**
     * Re-extract the libraries from our own base.apk to shared storage
     * (/sdcard/IronizedZink/lib/<abi>) and verify they load from there.
     * Requires storage access. Returns the per-file status at the shared location.
     */
    fun repairToSharedStorage(context: Context): Pair<File, List<LibStatus>> {
        val abi = primaryAbi()
        val outDir = File(Environment.getExternalStorageDirectory(), "${Zink.CONFIG_DIR}/lib/$abi")
        outDir.mkdirs()
        val apkPath = context.applicationInfo.sourceDir
        runCatching {
            ZipFile(apkPath).use { zip ->
                FILES.forEach { name ->
                    val entry = zip.getEntry("lib/$abi/$name") ?: return@forEach
                    zip.getInputStream(entry).use { input ->
                        File(outDir, name).outputStream().use { out -> input.copyTo(out) }
                    }
                }
            }
            Log.i(TAG, "extracted libs from apk to $outDir")
        }.onFailure { Log.w(TAG, "repair extract failed", it) }

        val statuses = FILES.map { name -> checkOne(File(outDir, name)) }
        return outDir to statuses
    }

    /** A copy-pasteable diagnostic report. */
    fun report(context: Context): String = buildString {
        appendLine("Ironized Zink — native library report")
        appendLine("app: ${context.packageName}  v${appVersion(context)}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("abi: ${primaryAbi()}  supported: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("nativeLibraryDir: ${nativeLibDir(context)}")
        appendLine("----")
        verify(context).forEach { s ->
            append(if (s.ok) "[OK]  " else "[FAIL] ")
            append(s.file)
            append("  exists=").append(s.exists)
            append(" size=").append(s.sizeBytes)
            if (s.loadTested) append(" loaded=").append(s.loaded)
            if (s.error != null) append("  err=").append(s.error)
            appendLine()
        }
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
