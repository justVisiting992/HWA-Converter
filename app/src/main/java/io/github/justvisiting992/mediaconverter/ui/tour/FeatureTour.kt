package io.github.justvisiting992.mediaconverter.ui.tour

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.R
import io.github.justvisiting992.mediaconverter.Tab
import kotlin.math.roundToInt

/** Anchor keys for the UI elements the tour points at. */
object TourKeys {
    const val ADD_FILES = "add_files"
    const val START = "start"
    const val PRESETS = "presets"
    const val NAV_QUEUE = "nav_queue"
    const val HARDWARE_HERO = "hardware_hero"
    const val NAV_SETTINGS = "nav_settings"
}

/**
 * Where tour targets are on screen, in window coordinates, reported by
 * the targets themselves as they are composed. Snapshot state: the
 * tour recomposes the moment a target appears after a tab switch.
 */
object TourBounds {
    val map = mutableStateMapOf<String, Rect>()
}

/** Mark this element as a tour target under [key]. */
fun Modifier.tourTarget(key: String): Modifier = onGloballyPositioned {
    TourBounds.map[key] = it.boundsInWindow()
}

/** Remembers whether the first-run tour has been completed. */
object TourPrefs {
    private const val PREFS = "feature_tour"
    private const val KEY = "seen"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}

private data class TourStep(
    val tab: Tab?,
    val targetKey: String?,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val TOUR_STEPS = listOf(
    TourStep(
        tab = null, targetKey = null,
        titleRes = R.string.tour_welcome_title,
        bodyRes = R.string.tour_welcome_body,
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.ADD_FILES,
        titleRes = R.string.tour_add_title,
        bodyRes = R.string.tour_add_body,
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.PRESETS,
        titleRes = R.string.tour_pick_title,
        bodyRes = R.string.tour_pick_body,
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.START,
        titleRes = R.string.tour_start_title,
        bodyRes = R.string.tour_start_body,
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.NAV_QUEUE,
        titleRes = R.string.tour_queue_title,
        bodyRes = R.string.tour_queue_body,
    ),
    TourStep(
        tab = Tab.HARDWARE, targetKey = TourKeys.HARDWARE_HERO,
        titleRes = R.string.tour_hardware_title,
        bodyRes = R.string.tour_hardware_body,
    ),
    TourStep(
        tab = Tab.SETTINGS, targetKey = TourKeys.NAV_SETTINGS,
        titleRes = R.string.tour_settings_title,
        bodyRes = R.string.tour_settings_body,
    ),
    TourStep(
        tab = null, targetKey = null,
        titleRes = R.string.tour_done_title,
        bodyRes = R.string.tour_done_body,
    ),
)

/**
 * The first-run feature tour, in the style of Google's app tours:
 * the screen dims, the element a step is about stays lit in a clean
 * rounded spotlight, and a simple card sits right against it, title,
 * one short text, step dots, and Skip / Next (Got it on the last
 * step) inside the card. No guide lines, no detached captions.
 * Deliberately light, one canvas, no blur, no animation loops.
 */
@Composable
internal fun FeatureTour(
    currentTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onFinish: () -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    val step = TOUR_STEPS[index]
    val isLast = index == TOUR_STEPS.lastIndex

    LaunchedEffect(index) {
        val target = step.tab
        if (target != null && target != currentTab) onSelectTab(target)
    }

    val hole: Rect? = step.targetKey?.let { TourBounds.map[it] }
    var cardRect by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* consume every touch: the tour owns the screen */ },
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 12.dp.toPx() }
        val padPx = with(density) { 6.dp.toPx() }

        // Card placement: right against the target, just below it
        // when it sits in the top half of the screen, just above it
        // otherwise; centered when the step has no target.
        val placeBelow = hole != null && hole.center.y < screenH * 0.5f
        val cardH = cardRect?.height ?: 0f
        val cardY: Float = when {
            hole == null -> (screenH - cardH) / 2f
            placeBelow -> hole.bottom + padPx + gapPx
            else -> hole.top - padPx - gapPx - cardH
        }.coerceIn(marginPx, (screenH - cardH - marginPx).coerceAtLeast(marginPx))

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (hole != null) {
                val cut = Rect(
                    hole.left - padPx, hole.top - padPx,
                    hole.right + padPx, hole.bottom + padPx,
                )
                val path = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(
                        RoundRect(cut, CornerRadius(18.dp.toPx(), 18.dp.toPx())),
                    )
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, Color.Black.copy(alpha = 0.72f))
            } else {
                drawRect(Color.Black.copy(alpha = 0.72f))
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .offset { IntOffset(marginPx.roundToInt(), cardY.roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { cardRect = it.boundsInWindow() },
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(step.titleRes), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(step.bodyRes), style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    // Step dots: the current step is a short pill,
                    // the rest are plain dots.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        TOUR_STEPS.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .width(if (i == index) 18.dp else 6.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (i == index) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.4f)
                                        },
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.tour_skip)) }
                    Button(
                        onClick = { if (isLast) onFinish() else index++ },
                    ) {
                        Text(stringResource(if (isLast) R.string.tour_got_it else R.string.tour_next))
                    }
                }
            }
        }
    }
}
