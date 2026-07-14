/*
 * Ironized Zink — startup verification & self-repair screen.
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var statuses by remember { mutableStateOf(NativeLibs.verify(context)) }
    var repairLog by remember { mutableStateOf<String?>(null) }
    var storageGranted by remember { mutableStateOf(SettingsRepository.hasStorageAccess(context)) }

    val manageStorage = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { storageGranted = SettingsRepository.hasStorageAccess(context) }
    val writePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { g -> storageGranted = g || SettingsRepository.hasStorageAccess(context) }

    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkg = Uri.parse("package:${context.packageName}")
            runCatching { manageStorage.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)) }
                .onFailure { runCatching { manageStorage.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    val allOk = statuses.all { it.ok }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ironized Zink", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Setup & verification", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            })
        },
    ) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allOk) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (allOk) "All renderer libraries verified" else "Some libraries need attention",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = if (allOk) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            if (allOk) "The Kopper Zink native stack is installed and loads correctly on this device."
                            else "One or more native libraries are missing or won't load. Use Repair below, then restart. Copy the report if it persists so it can be fixed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allOk) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            items(statuses.size) { i -> LibRow(statuses[i]) }

            if (!allOk) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Repair", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Re-extract the native libraries directly from the app package to " +
                                    "shared storage and re-test them. Requires storage access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!storageGranted) {
                                OutlinedButton(onClick = { requestStorage() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Grant storage access")
                                }
                            }
                            Button(
                                onClick = {
                                    val (dir, sts) = NativeLibs.repairToSharedStorage(context)
                                    repairLog = "Extracted to:\n$dir\n\n" +
                                        sts.joinToString("\n") { s ->
                                            "${if (s.ok) "OK  " else "FAIL"} ${s.file}  exists=${s.exists} loaded=${s.loaded}" +
                                                (s.error?.let { "\n     $it" } ?: "")
                                        }
                                    statuses = NativeLibs.verify(context)
                                },
                                enabled = storageGranted,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Repair & re-check") }
                        }
                    }
                }
                repairLog?.let { log ->
                    item {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                            Text(
                                log, style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(NativeLibs.report(context))) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy report") }
                        OutlinedButton(onClick = { restartApp() }, modifier = Modifier.weight(1f)) {
                            Text("Restart app")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(if (allOk) "Continue" else "Continue anyway")
                }
            }
        }
    }
}

@Composable
private fun LibRow(s: NativeLibs.LibStatus) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.ok) "OK" else "FAIL",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (s.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(s.file, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                val detail = buildString {
                    append(if (s.exists) "present (${s.sizeBytes / 1024} KB)" else "missing")
                    if (s.loadTested) append(if (s.loaded) " · loads" else " · load failed")
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.error?.takeIf { !s.ok }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
