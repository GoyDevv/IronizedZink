/*
 * Ironized Zink — GitHub Releases update checker.
 *
 * Queries the public GitHub REST API for the latest release of GoyDevv/IronizedZink,
 * compares its tag against the running versionName, and exposes the release notes
 * (changelog) and asset download URL for the in-app updater. No auth token is used
 * (public repo, unauthenticated rate limit is generously sufficient for a manual/
 * on-launch check).
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val TAG = "IronizedZink"
    private const val API_URL = "https://api.github.com/repos/GoyDevv/IronizedZink/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    data class ReleaseInfo(
        val tagName: String,
        val version: String,
        val name: String,
        val changelog: String,
        val apkUrl: String?,
        val apkName: String,
        val apkSizeBytes: Long,
        val htmlUrl: String,
    )

    sealed interface Result {
        data class UpdateAvailable(val release: ReleaseInfo) : Result
        data object UpToDate : Result
        data class Error(val message: String) : Result
    }

    /** Fetches the latest release and compares it against [currentVersion] (versionName). */
    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val release = runCatching { fetchLatest() }.getOrElse { t ->
            Log.w(TAG, "update check failed", t)
            return@withContext Result.Error(t.message ?: "Network error")
        } ?: return@withContext Result.Error("No release found")

        return@withContext if (isNewer(release.version, currentVersion)) {
            Result.UpdateAvailable(release)
        } else {
            Result.UpToDate
        }
    }

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "IronizedZink-UpdateChecker")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) return null
            val version = tag.removePrefix("v").trim()
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName = ""
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            return ReleaseInfo(
                tagName = tag,
                version = version,
                name = json.optString("name", tag),
                changelog = json.optString("body", "").ifBlank { "No changelog provided for this release." },
                apkUrl = apkUrl,
                apkName = apkName,
                apkSizeBytes = apkSize,
                htmlUrl = json.optString("html_url", "https://github.com/GoyDevv/IronizedZink/releases"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Simple dotted-numeric semver comparison; "1.0.10" > "1.0.9", extra segments default to 0. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".", "-", "+").mapNotNull { it.toIntOrNull() }
        val l = local.split(".", "-", "+").mapNotNull { it.toIntOrNull() }
        val len = maxOf(r.size, l.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
