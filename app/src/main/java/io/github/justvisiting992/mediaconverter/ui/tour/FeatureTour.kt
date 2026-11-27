package io.github.justvisiting992.mediaconverter.ui.tour

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.Tab
import kotlin.math.roundToInt

/** Anchor keys for the UI elements the tour points at. */
object TourKeys {
    const val ADD_FILES = "add_files"
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
    val title: String,
    val body: String,
)

private val TOUR_STEPS = listOf(
    TourStep(
        tab = null, targetKey = null,
        title = "Welcome to HWA Converter",
        body = "This app converts video and audio on your phone — on its " +
            "media engine first, not the CPU. A quick tour of where " +
            "things are.",
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.ADD_FILES,
        title = "Add files",
        body = "Tap this button and pick the videos or audio files you " +
            "want to convert.",
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.PRESETS,
        title = "Pick a conversion",
        body = "Hardware presets run on the media engine: fast, cool, " +
            "easy on the battery. Software presets trade speed for " +
            "maximum quality, and Custom lets you set everything " +
            "yourself. Tap a category to open it.",
    ),
    TourStep(
        tab = Tab.CONVERT, targetKey = TourKeys.NAV_QUEUE,
        title = "The Queue tab",
        body = "Every conversion lives in the Queue: live progress and " +
            "speed, the exact command that ran, and the debug log — " +
            "tap a finished job's log there to save it.",
    ),
    TourStep(
        tab = Tab.HARDWARE, targetKey = TourKeys.HARDWARE_HERO,
        title = "Your hardware",
        body = "This is your phone's real media engine, probed on this " +
            "device. The presets you are offered are built from this " +
            "— if a hardware path isn't listed, the chip doesn't " +
            "have it. Nothing is faked.",
    ),
    TourStep(
        tab = null, targetKey = TourKeys.NAV_SETTINGS,
        title = "Settings",
        body = "Themes, color schemes, and ten launcher icons to choose " +
            "from live here — plus a replay of this tour.",
    ),
    TourStep(
        tab = null, targetKey = null,
        title = "That's it",
        body = "One promise: the app has no network permission at all. " +
            "Your files never leave your phone.",
    ),
)

/**
 * The first-run feature tour: a dimmed overlay with a cutout around
 * the element each step is about, a guide line from the cutout to the
 * caption card, and Skip / Next controls. Deliberately light — one
 * canvas, no blur, no animation loops.
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
    val ringColor = MaterialTheme.colorScheme.primary
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
        val gapPx = with(density) { 30.dp.toPx() }
        val padPx = with(density) { 6.dp.toPx() }

        // Caption placement: below the target when it sits in the top
        // half of the screen, above it otherwise; centered when the
        // step has no target.
        val placeBelow = hole != null && hole.center.y < screenH * 0.5f
        val cardH = cardRect?.height ?: 0f
        val cardY: Float = when {
            hole == null -> (screenH - cardH) / 2f
            placeBelow -> hole.bottom + gapPx
            else -> hole.top - gapPx - cardH
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
                drawRoundRect(
                    color = ringColor,
                    topLeft = cut.topLeft,
                    size = cut.size,
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
                val card = cardRect
                if (card != null) {
                    // The guide line: from the cutout's edge to the
                    // caption card's edge, vertically aligned centers.
                    val start = Offset(
                        cut.center.x,
                        if (placeBelow) cut.bottom else cut.top,
                    )
                    val end = Offset(
                        cut.center.x.coerceIn(card.left + 24.dp.toPx(), card.right - 24.dp.toPx()),
                        if (placeBelow) card.top else card.bottom,
                    )
                    drawLine(
                        color = ringColor,
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(
                        color = ringColor,
                        radius = 3.5.dp.toPx(),
                        center = start,
                    )
                }
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
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Text(step.body, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${index + 1} / ${TOUR_STEPS.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { if (isLast) onFinish() else index++ },
                    ) {
                        Text(if (isLast) "Done" else if (index == 0) "Show me" else "Next")
                    }
                }
            }
        }
    }
}
