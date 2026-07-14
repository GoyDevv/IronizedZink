/*
 * Ironized Zink — in-app configuration UI (presets + advanced tuning).
 * Material 3 / Material You, dark-only, dynamic colour, Lexend, spring motion.
 * Licensed under GPL-3.0 (see LICENSE).
 */
package com.goydevv.ironizedzink

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            IronizedZinkTheme {
                var showApp by rememberSaveable { mutableStateOf(false) }
                if (showApp) IronizedZinkApp() else SetupScreen(onContinue = { showApp = true })
            }
        }
    }
}

private val OptionsSaver = mapSaver(
    save = { o ->
        mapOf(
            "gl" to o.glVersion, "tg" to o.threadedGl, "bc" to o.bigCoreAffinity,
            "vs" to o.vsync, "ne" to o.noError, "sc" to o.shaderCache,
            "sf" to o.singleFileCache, "rg" to o.relaxGlsl, "fps" to o.showFps,
            "sw" to o.forceSoftware,
        )
    },
    restore = { m ->
        RenderOptions(
            glVersion = m["gl"] as String, threadedGl = m["tg"] as Boolean,
            bigCoreAffinity = m["bc"] as Boolean, vsync = m["vs"] as Boolean,
            noError = m["ne"] as Boolean, shaderCache = m["sc"] as Boolean,
            singleFileCache = m["sf"] as Boolean, relaxGlsl = m["rg"] as Boolean,
            showFps = m["fps"] as Boolean, forceSoftware = m["sw"] as Boolean,
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
    var options by rememberSaveable(stateSaver = OptionsSaver) { mutableStateOf(SettingsRepository.loadOptions(context)) }
    var savedOptions by rememberSaveable(stateSaver = OptionsSaver) { mutableStateOf(SettingsRepository.loadOptions(context)) }
    var savedPresetName by rememberSaveable { mutableStateOf(SettingsRepository.loadPreset(context).name) }
    var storageGranted by rememberSaveable { mutableStateOf(SettingsRepository.hasStorageAccess(context)) }
    var showEnv by rememberSaveable { mutableStateOf(false) }

    val preset = runCatching { Preset.valueOf(presetName) }.getOrDefault(Preset.DEFAULT)
    val customized = !RenderOptions.matches(preset, options)
    val dirty = options != savedOptions || presetName != savedPresetName

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { storageGranted = SettingsRepository.hasStorageAccess(context) }
    val writePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> storageGranted = granted || SettingsRepository.hasStorageAccess(context) }

    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkg = Uri.parse("package:${context.packageName}")
            runCatching {
                manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
            }.onFailure {
                runCatching { manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ironized Zink", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("by GoyDevv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            })
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data, shape = RoundedCornerShape(14.dp)) }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    SettingsRepository.save(context, preset, options)
                    val wrote = SettingsRepository.exportConfig(context, options)
                    savedOptions = options
                    savedPresetName = presetName
                    scope.launch {
                        snackbar.showSnackbar(
                            when {
                                !storageGranted -> "Saved — grant storage so it applies in-game"
                                wrote -> "Saved — will apply next time you launch"
                                else -> "Saved"
                            }
                        )
                    }
                },
            ) { Text(if (dirty) "Save settings" else "Saved") }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TechCaption() }

            if (!storageGranted) item { StorageCard(onGrant = { requestStorage() }) }

            item { SectionTitle("Presets") }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(Preset.entries, key = { it.name }) { p ->
                        PresetChip(
                            preset = p,
                            selected = p == preset,
                            onSelect = { presetName = p.name; options = RenderOptions.forPreset(p) },
                        )
                    }
                }
            }
            item { PresetDetails(preset = preset, customized = customized) }

            item { SectionTitle("Advanced") }
            item { AdvancedCard(options = options, onChange = { options = it }) }

            item { EnvSection(env = buildEnv(options), expanded = showEnv, onToggle = { showEnv = !showEnv }) {
                clipboard.setText(AnnotatedString(buildEnv(options).toColonEnv()))
                scope.launch { snackbar.showSnackbar("Environment copied") }
            } }

            item { CreditsCard() }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TechCaption() {
    Text(
        "Zink + Kopper · desktop OpenGL 4.6 over Vulkan · Mesa ${Zink.MESA_VERSION}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun StorageCard(onGrant: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enable in-game settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                "To make presets and options actually change the game, Ironized Zink saves a " +
                    "tiny config to shared storage that the launcher reads at launch. Grant " +
                    "storage access once to enable this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onGrant) { Text("Grant storage access") }
        }
    }
}

@Composable
private fun PresetChip(preset: Preset, selected: Boolean, onSelect: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "chip",
    )
    val onColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val scale by animateFloatAsState(if (selected) 1f else 0.95f, Bouncy, label = "chipScale")
    Card(
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(preset.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onColor)
            Text(preset.tagline, style = MaterialTheme.typography.labelSmall, color = onColor)
            if (preset.recommended) {
                Text("Recommended", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = onColor)
            }
        }
    }
}

@Composable
private fun PresetDetails(preset: Preset, customized: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (preset.recommended) { Spacer(Modifier.padding(start = 6.dp)); Badge("Recommended") }
                if (customized) { Spacer(Modifier.padding(start = 6.dp)); Badge("Customized") }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    preset.expect,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Text(preset.description, style = MaterialTheme.typography.bodySmall)
            SpecRow("Minecraft", preset.mcVersions)
            SpecRow("Shaders", preset.shaders)
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$label:  ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Badge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AdvancedCard(options: RenderOptions, onChange: (RenderOptions) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Text("OpenGL version", style = MaterialTheme.typography.labelLarge)
            Text(
                "Higher = newer Minecraft & shaders. 3.3 is lighter but won't run 1.21+/26.x.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4.6", "4.5", "4.3", "3.3").forEach { v ->
                    FilterChip(selected = options.glVersion == v, onClick = { onChange(options.copy(glVersion = v)) }, label = { Text(v) })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            ToggleRow("Threaded GL", "mesa_glthread — offloads GL to a worker thread; higher FPS", options.threadedGl) { onChange(options.copy(threadedGl = it)) }
            ToggleRow("Big-core affinity", "Pin rendering to the performance CPU cores", options.bigCoreAffinity) { onChange(options.copy(bigCoreAffinity = it)) }
            ToggleRow("VSync", "On = smooth, tear-free. Off = uncapped, maximum FPS", options.vsync) { onChange(options.copy(vsync = it)) }
            ToggleRow("No-error fast path", "MESA_NO_ERROR — skips GL error checks; faster, less safe", options.noError) { onChange(options.copy(noError = it)) }
            ToggleRow("Shader disk cache", "Cache compiled shaders for faster warm loads", options.shaderCache) { onChange(options.copy(shaderCache = it)) }
            ToggleRow("Single-file cache", "Store the shader cache as one file (less I/O)", options.singleFileCache) { onChange(options.copy(singleFileCache = it)) }
            ToggleRow("Relaxed GLSL", "Compatibility knobs many shaders & mods rely on", options.relaxGlsl) { onChange(options.copy(relaxGlsl = it)) }
            ToggleRow("FPS overlay", "GALLIUM_HUD — draw an on-screen frame-rate counter", options.showFps) { onChange(options.copy(showFps = it)) }
            ToggleRow("Force software", "LIBGL_ALWAYS_SOFTWARE — CPU fallback; last-resort only", options.forceSoftware) { onChange(options.copy(forceSoftware = it)) }
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
private fun EnvSection(
    env: LinkedHashMap<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Environment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${env.size} variables applied at launch", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onToggle) { Text(if (expanded) "Hide" else "Show") }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            env.entries.joinToString("\n") { "${it.key}=${it.value}" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCopy) { Text("Copy for launcher custom-env") }
                }
            }
        }
    }
}

@Composable
private fun CreditsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ironized Zink by GoyDevv", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Renderer by the Zink / Mesa 3D project (MIT). Kopper WSI by Mesa. Plugin " +
                    "interface & modified Android Zink build by the Fold Craft Launcher team (GPL-3.0). " +
                    "Lexend font under the SIL OFL 1.1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
