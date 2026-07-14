/*
 * Ironized Zink — in-app configuration UI (presets + advanced tuning).
 * Material 3 / Material You, dark-only, dynamic colour, Lexend, spring motion.
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goydevv.ironizedzink.ui.theme.IronizedZinkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IronizedZinkTheme { IronizedZinkApp() }
        }
    }
}

/** Saver so unsaved edits survive configuration changes. */
private val OptionsSaver = mapSaver(
    save = { o ->
        mapOf(
            "glVersion" to o.glVersion,
            "threadedGl" to o.threadedGl,
            "showFps" to o.showFps,
            "singleFileCache" to o.singleFileCache,
            "noError" to o.noError,
            "relaxGlsl" to o.relaxGlsl,
            "allExtensions" to o.allExtensions,
            "forceSoftware" to o.forceSoftware,
        )
    },
    restore = { m ->
        RenderOptions(
            glVersion = m["glVersion"] as String,
            threadedGl = m["threadedGl"] as Boolean,
            showFps = m["showFps"] as Boolean,
            singleFileCache = m["singleFileCache"] as Boolean,
            noError = m["noError"] as Boolean,
            relaxGlsl = m["relaxGlsl"] as Boolean,
            allExtensions = m["allExtensions"] as Boolean,
            forceSoftware = m["forceSoftware"] as Boolean,
        )
    },
)

private val Bouncy = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronizedZinkApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var presetName by rememberSaveable { mutableStateOf(SettingsRepository.loadPreset(context).name) }
    var options by rememberSaveable(stateSaver = OptionsSaver) {
        mutableStateOf(SettingsRepository.loadOptions(context))
    }
    var savedOptions by rememberSaveable(stateSaver = OptionsSaver) {
        mutableStateOf(SettingsRepository.loadOptions(context))
    }
    var savedPresetName by rememberSaveable { mutableStateOf(SettingsRepository.loadPreset(context).name) }

    val preset = runCatching { Preset.valueOf(presetName) }.getOrDefault(Preset.DEFAULT)
    val dirty = options != savedOptions || presetName != savedPresetName
    val env = buildEnv(options)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Ironized Zink", fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    SettingsRepository.save(context, preset, options)
                    SettingsRepository.exportConfig(context, options)
                    savedOptions = options
                    savedPresetName = presetName
                    scope.launch { snackbar.showSnackbar("Settings saved & exported") }
                },
            ) {
                Text(if (dirty) "Save settings" else "Saved")
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeaderCard() }

            item { SectionTitle("Presets") }
            items(Preset.entries, key = { it.name }) { p ->
                PresetCard(
                    preset = p,
                    selected = p == preset,
                    onSelect = {
                        presetName = p.name
                        options = RenderOptions.forPreset(p)
                    },
                )
            }

            item { SectionTitle("Advanced") }
            item { AdvancedCard(options = options, onChange = { options = it }) }

            item { SectionTitle("Generated environment") }
            item {
                EnvCard(
                    colonEnv = env.toColonEnv(),
                    onCopy = {
                        clipboard.setText(AnnotatedString(env.toColonEnv()))
                        scope.launch { snackbar.showSnackbar("Environment copied to clipboard") }
                    },
                )
            }

            item { SectionTitle("About & credits") }
            item { CreditsCard() }
            item { Spacer(Modifier.height(8.dp)) }
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
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
private fun HeaderCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Kopper Zink",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Desktop OpenGL 4.6 over Vulkan — the Mesa Zink Gallium driver " +
                    "(v${Zink.MESA_VERSION}) with the Kopper WSI.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Renderer plugin for ZalithLauncher 2 & the Fold Craft Launcher family. " +
                    "Install this APK, then pick \"Ironized Zink\" in your launcher's renderer list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, selected: Boolean, onSelect: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "container",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = Bouncy,
        label = "scale",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "elevation",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(Modifier.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(preset.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(preset.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                    Text("Minecraft: ${preset.mcVersions}", style = MaterialTheme.typography.labelMedium)
                    Text("Shaders: ${preset.shaders}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AdvancedCard(options: RenderOptions, onChange: (RenderOptions) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Text("OpenGL version", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4.6", "4.5", "4.3", "3.3").forEach { v ->
                    FilterChip(
                        selected = options.glVersion == v,
                        onClick = { onChange(options.copy(glVersion = v)) },
                        label = { Text(v) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            ToggleRow("Threaded GL", "mesa_glthread — higher FPS on most devices", options.threadedGl) {
                onChange(options.copy(threadedGl = it))
            }
            ToggleRow("FPS overlay", "GALLIUM_HUD — on-screen frame-rate counter", options.showFps) {
                onChange(options.copy(showFps = it))
            }
            ToggleRow("Single-file shader cache", "Faster loads, less disk I/O", options.singleFileCache) {
                onChange(options.copy(singleFileCache = it))
            }
            ToggleRow("No-error fast path", "MESA_NO_ERROR — faster, skips GL error checks", options.noError) {
                onChange(options.copy(noError = it))
            }
            ToggleRow("Relaxed GLSL", "Compatibility knobs shaders & mods rely on", options.relaxGlsl) {
                onChange(options.copy(relaxGlsl = it))
            }
            ToggleRow("Expose all extensions", "No extension year cap", options.allExtensions) {
                onChange(options.copy(allExtensions = it))
            }
            ToggleRow("Force software", "LIBGL_ALWAYS_SOFTWARE — last-resort CPU fallback", options.forceSoftware) {
                onChange(options.copy(forceSoftware = it))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun EnvCard(colonEnv: String, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "These are the variables the launcher applies. The plugin already ships the " +
                    "baseline in its manifest; paste this into your launcher's custom-environment " +
                    "field to apply a preset everywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
            ) {
                Text(
                    text = colonEnv.replace(":", "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                )
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("Copy environment") }
        }
    }
}

@Composable
private fun CreditsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Credits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "• Zink & the Mesa 3D Graphics Library — the OpenGL-over-Vulkan driver at the " +
                    "heart of this plugin. © the Mesa authors, MIT licensed.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• Kopper — Mesa's window-system integration that lets Zink present through " +
                    "Vulkan swapchains.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "• The Fold Craft Launcher team — for the renderer-plugin interface this plugin " +
                    "targets. GPL-3.0.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                "Ironized Zink integration code: GPL-3.0. Bundled Mesa/Zink binaries: MIT. " +
                    "Lexend font: SIL OFL 1.1. Full licenses are in this app's assets.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
