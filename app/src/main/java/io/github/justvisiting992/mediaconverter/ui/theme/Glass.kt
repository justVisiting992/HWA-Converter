package io.github.justvisiting992.mediaconverter.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Liquid glass, the cheap kind. Real backdrop blur (sampling and
 * blurring whatever scrolls behind a bar, every frame) is a GPU pass
 * this app refuses to pay for — it costs battery to render a converter
 * nobody is looking at while it works. What the eye reads as "glass"
 * is mostly three static things: a translucent surface, a bright
 * hairline edge, and a soft shadow with a faint sheen on top. Those
 * are two background draws and a border, cached by the UI toolkit and
 * costing nothing per frame. No new libraries, no blur passes, no
 * RenderEffect — the APK doesn't grow and the GPU doesn't work harder.
 *
 * Used only where UI actually floats over scrolling content (the top
 * pill and the navigation pill), which is where glass means anything.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    val edge = Brush.verticalGradient(
        listOf(
            scheme.onSurface.copy(alpha = 0.18f),
            scheme.onSurface.copy(alpha = 0.06f),
        ),
    )
    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.09f),
            Color.White.copy(alpha = 0.0f),
        ),
    )
    return this
        .shadow(elevation = 8.dp, shape = shape, clip = false)
        .background(
            color = scheme.surfaceContainerHigh.copy(alpha = 0.86f),
            shape = shape,
        )
        .background(brush = sheen, shape = shape)
        .border(width = 1.dp, brush = edge, shape = shape)
}
