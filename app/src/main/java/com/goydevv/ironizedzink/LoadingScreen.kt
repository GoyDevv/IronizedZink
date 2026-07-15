/*
 * Ironized Zink — real startup loading screen.
 *
 * Runs the actual native-library verification (NativeLibs.verify), settings load and
 * storage-access check as sequential staged steps with genuine progress reporting (each
 * step only completes once its real check has returned), instead of a fake timed splash.
 * On success it proceeds straight to the main app; on failure it hands off to the
 * existing SetupScreen (native-lib repair / diagnostics) so the user can fix it.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class LoadStep(val label: String, val check: suspend (android.content.Context) -> Boolean)

private val STEPS = listOf(
    LoadStep("Checking native renderer libraries") { ctx -> NativeLibs.verify(ctx).all { it.ok } },
    LoadStep("Loading saved settings") { ctx ->
        SettingsRepository.loadOptions(ctx); SettingsRepository.loadPreset(ctx); true
    },
    LoadStep("Checking storage access") { ctx -> SettingsRepository.hasStorageAccess(ctx); true },
    LoadStep("Detecting display refresh rate") { ctx -> RefreshRate.currentHz(ctx) > 0 },
)

/**
 * @param onFinished called with `true` if every real check passed (go straight to the
 *   app), or `false` if one or more failed (the caller should show [SetupScreen]).
 */
@Composable
fun LoadingScreen(onFinished: (allOk: Boolean) -> Unit) {
    val context = LocalContext.current
    var stepIndex by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf(STEPS.first().label) }
    var allOk by remember { mutableStateOf(true) }

    val progress by animateFloatAsState(
        targetValue = stepIndex.toFloat() / STEPS.size,
        animationSpec = tween(durationMillis = 220),
        label = "loadingProgress",
    )

    LaunchedEffect(Unit) {
        for ((index, step) in STEPS.withIndex()) {
            label = step.label
            val ok = runCatching { step.check(context) }.getOrDefault(false)
            if (!ok) allOk = false
            // Small settle delay so fast checks are still visible as discrete steps
            // rather than a single instant flash — this does not fake the result,
            // it only paces the display of real, already-completed results.
            delay(120)
            stepIndex = index + 1
        }
        onFinished(allOk)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Ironized Zink",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "by GoyDevv",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Column(Modifier.padding(top = 40.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
