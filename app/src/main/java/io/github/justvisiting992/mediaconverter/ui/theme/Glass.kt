package io.github.justvisiting992.mediaconverter.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The floating bars' surface: a plain, solid Material container color
 * with a standard elevation shadow. This file used to hold a simulated
 * "liquid glass" (translucency, sheen, hairline edge). It was removed
 * on sight — it read as mere transparency, and real backdrop blur
 * would cost a GPU pass per frame on a phone whose battery this app
 * exists to save. Material's own floating surfaces are the standard;
 * this is that, nothing fancier.
 */
@Composable
fun Modifier.floatingSurface(shape: Shape): Modifier =
    this
        .shadow(elevation = 8.dp, shape = shape, clip = false)
        .background(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape,
        )
