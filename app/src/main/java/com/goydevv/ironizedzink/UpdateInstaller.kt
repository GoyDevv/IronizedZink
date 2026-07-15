/*
 * Ironized Zink — in-app update downloader/installer.
 *
 * Downloads the release APK asset (from the GitHub Releases API response) straight into
 * the app's cache directory using Android's DownloadManager, reports real progress, and
 * then hands the file to the system package installer via a FileProvider content:// URI
 * (REQUEST_INSTALL_PACKAGES + ACTION_VIEW), so the user never has to open a browser.
 * The install prompt itself is still shown by the OS (required — apps cannot silently
 * install APKs without either being a device owner or the user confirming the system
 * installer dialog), but no browser or manual download step is needed.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

object UpdateInstaller {

    sealed interface DownloadState {
        data object Starting : DownloadState
        data class Progress(val bytesDownloaded: Long, val bytesTotal: Long) : DownloadState
        data class Done(val file: File) : DownloadState
        data class Failed(val reason: String) : DownloadState
    }

    private fun apkFile(context: Context, fileName: String): File =
        File(context.cacheDir, "updates").apply { mkdirs() }.let { File(it, fileName) }

    /** True if the OS will let this app prompt the install dialog directly. */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Downloads [release]'s APK asset, emitting real progress from DownloadManager's own cursor. */
    fun download(context: Context, release: UpdateChecker.ReleaseInfo): Flow<DownloadState> = flow {
        val url = release.apkUrl
        if (url.isNullOrBlank()) {
            emit(DownloadState.Failed("This release has no APK asset attached"))
            return@flow
        }
        emit(DownloadState.Starting)

        val fileName = release.apkName.ifBlank { "Ironized-Zink-${release.tagName}.apk" }
        val destFile = apkFile(context, fileName)
        if (destFile.exists()) destFile.delete()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Ironized Zink ${release.tagName}")
            .setDescription("Downloading update…")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)

        val id = runCatching { dm.enqueue(request) }.getOrElse {
            emit(DownloadState.Failed(it.message ?: "Could not start download"))
            return@flow
        }

        var downloading = true
        while (downloading) {
            val query = DownloadManager.Query().setFilterById(id)
            val cursor: Cursor = dm.query(query) ?: break
            var status = -1
            var soFar = 0L
            var total = release.apkSizeBytes
            cursor.use { c ->
                if (!c.moveToFirst()) return@use
                val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                status = if (statusIdx >= 0) c.getInt(statusIdx) else -1
                soFar = if (soFarIdx >= 0) c.getLong(soFarIdx) else 0L
                val totalRaw = if (totalIdx >= 0) c.getLong(totalIdx) else -1L
                if (totalRaw > 0) total = totalRaw
            }

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> downloading = false
                DownloadManager.STATUS_FAILED -> {
                    emit(DownloadState.Failed("Download failed"))
                    return@flow
                }
                else -> emit(DownloadState.Progress(soFar, total))
            }
            if (downloading) delay(200)
        }

        if (destFile.exists() && destFile.length() > 0) {
            emit(DownloadState.Done(destFile))
        } else {
            emit(DownloadState.Failed("Downloaded file is missing or empty"))
        }
    }

    /** Builds the system installer intent for an already-downloaded APK file. */
    fun installIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
