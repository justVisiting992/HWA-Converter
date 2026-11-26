package io.github.justvisiting992.mediaconverter

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.capability.HardwareCodecProbe
import io.github.justvisiting992.mediaconverter.ui.capability.CapabilityScreen
import io.github.justvisiting992.mediaconverter.ui.convert.ConvertScreen
import io.github.justvisiting992.mediaconverter.ui.settings.SettingsScreen
import io.github.justvisiting992.mediaconverter.ui.theme.MediaConverterTheme
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeMode
import io.github.justvisiting992.mediaconverter.ui.theme.ThemePrefs
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeTemplate
import io.github.justvisiting992.mediaconverter.ui.theme.floatingSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val title: String, val icon: ImageVector) {
    CONVERT("Convert", Icons.Filled.SwapHoriz),
    HARDWARE("Your hardware", Icons.Filled.Memory),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
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
                // Floating chrome: content fills the screen and scrolls
                // under a floating navigation pill, instead of living
                // between two fixed bars. The screens pad their own
                // content clear of the pill.
                // The root Surface also provides the theme's content
                // color — without it, plain text falls back to black.
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val direction =
                                if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn() + slideInHorizontally { it / 6 * direction })
                                .togetherWith(
                                    fadeOut() + slideOutHorizontally { -it / 6 * direction },
                                )
                        },
                        label = "tab",
                    ) { currentTab ->
                        when (currentTab) {
                            Tab.CONVERT -> ConvertScreen(reportState.value, Modifier.fillMaxSize().statusBarsPadding())
                            Tab.HARDWARE -> CapabilityScreen(reportState.value, Modifier.fillMaxSize().statusBarsPadding())
                            Tab.SETTINGS -> SettingsScreen(
                                template = template,
                                mode = mode,
                                onTemplateChange = { update(it, mode) },
                                onModeChange = { update(template, it) },
                                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            )
                        }
                    }
                    FloatingNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
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

/** The floating navigation pill: three tabs, selected one filled. */
@Composable
private fun FloatingNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .floatingSurface(shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { entry ->
            NavPillItem(
                entry = entry,
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun NavPillItem(entry: Tab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            entry.icon,
            contentDescription = entry.title,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        AnimatedVisibility(visible = selected) {
            Text(
                entry.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
