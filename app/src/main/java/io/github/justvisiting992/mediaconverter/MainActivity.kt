package io.github.justvisiting992.mediaconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import io.github.justvisiting992.mediaconverter.ui.capability.CapabilityScreen
import io.github.justvisiting992.mediaconverter.ui.theme.MediaConverterTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaConverterTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Your hardware") }) },
                ) { innerPadding ->
                    CapabilityScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}
