/*
 * Ironized Zink — update available dialog.
 *
 * Shown when UpdateChecker finds a GitHub release newer than the running versionName.
 * Presents the full changelog (release notes body, scrollable, un-truncated) and lets
 * the user either dismiss ("Later") or download + install in-app ("Update") without
 * leaving the app or opening a browser.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** UI state for the update flow — mirrors [UpdateInstaller.DownloadState] plus the idle case. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Downloading : UpdateUiState
    data class Progress(val fraction: Float, val downloadedMb: Double, val totalMb: Double) : UpdateUiState
    data class ReadyToInstall(val file: java.io.File) : UpdateUiState
    data class Failed(val reason: String) : UpdateUiState
}

@Composable
fun UpdateDialog(
    release: UpdateChecker.ReleaseInfo,
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onLater: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text("Update available", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${release.tagName} is out — you're on an older version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                Text(
                    "Changelog",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        release.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }

                when (state) {
                    is UpdateUiState.Downloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 10.dp).width(20.dp).heightIn(20.dp, 20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Starting download…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UpdateUiState.Progress -> {
                        Column(Modifier.padding(top = 12.dp)) {
                            LinearProgressIndicator(
                                progress = { state.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                if (state.totalMb > 0)
                                    "%.1f MB / %.1f MB".format(state.downloadedMb, state.totalMb)
                                else "%.1f MB downloaded".format(state.downloadedMb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Text(
                            "Downloaded. Tap Install to continue in the system installer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    is UpdateUiState.Failed -> {
                        Text(
                            "Update failed: ${state.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    UpdateUiState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.ReadyToInstall -> Button(onClick = { onInstallClick(state.file) }) { Text("Install") }
                is UpdateUiState.Downloading, is UpdateUiState.Progress -> Button(onClick = {}, enabled = false) { Text("Downloading…") }
                else -> Button(onClick = onUpdateClick) { Text("Update") }
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Later") }
        },
    )
}
