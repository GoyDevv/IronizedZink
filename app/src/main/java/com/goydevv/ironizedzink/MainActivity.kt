/*
 * Ironized Zink — in-app configuration UI (presets + advanced tuning).
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val IronScheme = darkColorScheme(
    primary = Color(0xFF8FB3D9),        // cool steel blue
    onPrimary = Color(0xFF0B1622),
    secondary = Color(0xFFB8C4CF),
    background = Color(0xFF11151A),
    surface = Color(0xFF171C22),
    surfaceVariant = Color(0xFF222A32),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = IronScheme) {
                IronizedZinkApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronizedZinkApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var preset by remember { mutableStateOf(SettingsRepository.loadPreset(context)) }
    var options by remember { mutableStateOf(SettingsRepository.loadOptions(context)) }

    val env = buildEnv(options)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Ironized Zink") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderCard()

            SectionTitle("Presets")
            Preset.entries.forEach { p ->
                PresetCard(
                    preset = p,
                    selected = p == preset,
                    onSelect = {
                        preset = p
                        options = RenderOptions.forPreset(p)
                    },
                )
            }

            SectionTitle("Advanced")
            AdvancedCard(options = options, onChange = { options = it })

            SectionTitle("Generated environment")
            EnvCard(
                colonEnv = env.toColonEnv(),
                onCopy = {
                    clipboard.setText(AnnotatedString(env.toColonEnv()))
                    scope.launch { snackbar.showSnackbar("Environment copied to clipboard") }
                },
                onSave = {
                    SettingsRepository.save(context, preset, options)
                    val path = SettingsRepository.exportConfig(context, options)
                    scope.launch {
                        snackbar.showSnackbar(
                            if (path != null) "Saved • config written" else "Saved",
                        )
                    }
                },
            )

            SectionTitle("About & credits")
            CreditsCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HeaderCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Kopper Zink",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Desktop OpenGL 4.6 over Vulkan — powered by the Mesa Zink " +
                    "Gallium driver (v${Zink.MESA_VERSION}) with the Kopper WSI.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Renderer plugin for ZalithLauncher 2 & Fold Craft Launcher family. " +
                    "Install this APK, then pick \"Ironized Zink\" in your launcher's " +
                    "renderer list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(preset.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
                Text("Minecraft: ${preset.mcVersions}", style = MaterialTheme.typography.labelMedium)
                Text("Shaders: ${preset.shaders}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AdvancedCard(options: RenderOptions, onChange: (RenderOptions) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OpenGL version", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4.6", "4.5", "4.3", "3.3").forEach { v ->
                    FilterChip(
                        selected = options.glVersion == v,
                        onClick = { onChange(options.copy(glVersion = v)) },
                        label = { Text(v) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ToggleRow("Threaded GL (mesa_glthread)", "Higher FPS on most devices", options.threadedGl) {
                onChange(options.copy(threadedGl = it))
            }
            ToggleRow("FPS overlay (GALLIUM_HUD)", "Draw an on-screen frame-rate counter", options.showFps) {
                onChange(options.copy(showFps = it))
            }
            ToggleRow("Single-file shader cache", "Faster loads, less disk I/O", options.singleFileCache) {
                onChange(options.copy(singleFileCache = it))
            }
            ToggleRow("No-error fast path (MESA_NO_ERROR)", "Skips GL error checks — faster, less safe", options.noError) {
                onChange(options.copy(noError = it))
            }
            ToggleRow("Relaxed GLSL", "Compatibility knobs shaders/mods rely on", options.relaxGlsl) {
                onChange(options.copy(relaxGlsl = it))
            }
            ToggleRow("Expose all extensions", "No extension year cap", options.allExtensions) {
                onChange(options.copy(allExtensions = it))
            }
            ToggleRow("Force software (LIBGL_ALWAYS_SOFTWARE)", "CPU fallback — last-resort compatibility", options.forceSoftware) {
                onChange(options.copy(forceSoftware = it))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun EnvCard(colonEnv: String, onCopy: () -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "These variables are what the launcher applies. The plugin already ships " +
                    "the baseline in its manifest; paste this into your launcher's custom " +
                    "environment field to apply a preset everywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = colonEnv.replace(":", "\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save & export") }
            }
        }
    }
}

@Composable
private fun CreditsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Credits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "• Zink & the Mesa 3D Graphics Library — the OpenGL-over-Vulkan driver at the " +
                    "heart of this plugin. © the Mesa authors, MIT licensed.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Kopper — Mesa's window-system integration that makes Zink present through " +
                    "Vulkan swapchains.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• The Fold Craft Launcher (FCL) team — for the renderer-plugin interface this " +
                    "plugin targets. GPL-3.0.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                "Ironized Zink integration code: GPL-3.0. Bundled Mesa/Zink binaries: MIT " +
                    "(see the licenses in this app's assets).",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}
