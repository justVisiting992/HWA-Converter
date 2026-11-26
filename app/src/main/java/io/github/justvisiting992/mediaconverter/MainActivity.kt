package io.github.justvisiting992.mediaconverter

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.capability.HardwareCodecProbe
import io.github.justvisiting992.mediaconverter.ui.capability.CapabilityScreen
import io.github.justvisiting992.mediaconverter.ui.convert.ConvertScreen
import io.github.justvisiting992.mediaconverter.ui.settings.SettingsScreen
import io.github.justvisiting992.mediaconverter.ui.theme.MediaConverterTheme
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeMode
import io.github.justvisiting992.mediaconverter.ui.theme.ThemePrefs
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val title: String) {
    CONVERT("Convert"),
    HARDWARE("Your hardware"),
    SETTINGS("Settings"),
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        setContent {
            var appearance by remember {
                mutableStateOf(ThemePrefs.load(applicationContext))
            }
            val (template, mode) = appearance
            fun update(newTemplate: ThemeTemplate, newMode: ThemeMode) {
                appearance = newTemplate to newMode
                ThemePrefs.save(applicationContext, newTemplate, newMode)
            }
            MediaConverterTheme(template = template, mode = mode) {
                // The capability report is probed once here and shared:
                // the Convert tab builds its presets from the same report
                // the Hardware tab shows the user.
                val reportState = produceState<DeviceCodecReport?>(initialValue = null) {
                    value = withContext(Dispatchers.Default) { HardwareCodecProbe.probe() }
                }
                var tab by remember { mutableStateOf(Tab.CONVERT) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text(tab.title) }) },
                    bottomBar = { TabBar(tab) { tab = it } },
                ) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    when (tab) {
                        Tab.CONVERT -> ConvertScreen(reportState.value, modifier)
                        Tab.HARDWARE -> CapabilityScreen(reportState.value, modifier)
                        Tab.SETTINGS -> SettingsScreen(
                            template = template,
                            mode = mode,
                            onTemplateChange = { update(it, mode) },
                            onModeChange = { update(template, it) },
                            modifier = modifier,
                        )
                    }
                }
            }
        }
    }

    /**
     * Ask the display for its fastest mode at the current resolution.
     * Phones with per-app refresh control (Xiaomi/HyperOS among them)
     * hold apps at 60 Hz unless the app requests a higher rate; this is
     * the platform's request mechanism. The system's per-app setting
     * can still override it — nothing app-side can outrank that, and
     * the app doesn't pretend otherwise.
     */
    private fun requestHighestRefreshRate() {
        val display: Display? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION") windowManager.defaultDisplay
            }
        val modes = display?.supportedModes ?: return
        val current = display.mode ?: return
        val fastest = modes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return
        if (fastest.refreshRate > current.refreshRate) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = fastest.modeId
                preferredRefreshRate = fastest.refreshRate
            }
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == Tab.CONVERT,
            onClick = { onSelect(Tab.CONVERT) },
            icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
            label = { Text("Convert") },
        )
        NavigationBarItem(
            selected = selected == Tab.HARDWARE,
            onClick = { onSelect(Tab.HARDWARE) },
            icon = { Icon(Icons.Filled.Memory, contentDescription = null) },
            label = { Text("Hardware") },
        )
        NavigationBarItem(
            selected = selected == Tab.SETTINGS,
            onClick = { onSelect(Tab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}
