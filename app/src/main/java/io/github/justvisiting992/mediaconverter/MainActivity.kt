package io.github.justvisiting992.mediaconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
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
import io.github.justvisiting992.mediaconverter.ui.theme.MediaConverterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val title: String) {
    CONVERT("Convert"),
    HARDWARE("Your hardware"),
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaConverterTheme {
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
                    }
                }
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
    }
}
