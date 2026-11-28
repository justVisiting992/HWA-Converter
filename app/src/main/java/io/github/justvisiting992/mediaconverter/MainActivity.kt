package io.github.justvisiting992.mediaconverter

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.justvisiting992.mediaconverter.core.capability.DeviceCodecReport
import io.github.justvisiting992.mediaconverter.core.capability.HardwareCodecProbe
import io.github.justvisiting992.mediaconverter.core.locale.AppLocale
import io.github.justvisiting992.mediaconverter.core.locale.Strings
import io.github.justvisiting992.mediaconverter.core.update.UpdateController
import io.github.justvisiting992.mediaconverter.ui.capability.CapabilityScreen
import io.github.justvisiting992.mediaconverter.ui.about.AboutScreen
import io.github.justvisiting992.mediaconverter.ui.convert.ConvertScreen
import io.github.justvisiting992.mediaconverter.ui.queue.QueueScreen
import io.github.justvisiting992.mediaconverter.ui.settings.SettingsScreen
import io.github.justvisiting992.mediaconverter.ui.theme.MediaConverterTheme
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeMode
import io.github.justvisiting992.mediaconverter.ui.theme.ThemePrefs
import io.github.justvisiting992.mediaconverter.ui.theme.ThemeTemplate
import io.github.justvisiting992.mediaconverter.ui.theme.VazirFamily
import io.github.justvisiting992.mediaconverter.ui.theme.floatingSurface
import io.github.justvisiting992.mediaconverter.ui.update.UpdateDialog
import io.github.justvisiting992.mediaconverter.ui.tour.FeatureTour
import io.github.justvisiting992.mediaconverter.ui.tour.TourKeys
import io.github.justvisiting992.mediaconverter.ui.tour.TourPrefs
import io.github.justvisiting992.mediaconverter.ui.tour.tourTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class Tab(@StringRes val titleRes: Int, val icon: ImageVector) {
    CONVERT(R.string.tab_convert, Icons.Filled.SwapHoriz),
    QUEUE(R.string.tab_queue, Icons.Filled.Queue),
    HARDWARE(R.string.tab_hardware, Icons.Filled.Memory),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
    ABOUT(R.string.tab_about, Icons.Filled.Info),
}

class MainActivity : ComponentActivity() {

    /**
     * The stored language wraps the activity before the first frame,
     * so the app opens in the chosen language, never flashing the
     * system one first. The same wrap is reapplied live, through
     * LocalContext, when the user picks another language.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Strings.bind(applicationContext)
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
            var languageTag by remember {
                mutableStateOf(AppLocale.selectedTag(applicationContext))
            }
            val localizedContext = remember(languageTag) {
                AppLocale.wrap(applicationContext, languageTag)
            }
            val layoutDirection = if (AppLocale.isRtl(applicationContext, languageTag)) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
            val fontFamily = if (AppLocale.isPersian(applicationContext, languageTag)) {
                VazirFamily
            } else {
                FontFamily.Default
            }
            fun changeLanguage(tag: String) {
                AppLocale.setTag(applicationContext, tag)
                Strings.bind(applicationContext)
                languageTag = tag
            }
            MediaConverterTheme(
                template = template,
                mode = mode,
                fontFamily = fontFamily,
            ) {
              CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides layoutDirection,
              ) {
                // The capability report is probed once here and shared:
                // the Convert tab builds its presets from the same report
                // the Hardware tab shows the user.
                val reportState = produceState<DeviceCodecReport?>(initialValue = null) {
                    value = withContext(Dispatchers.Default) { HardwareCodecProbe.probe() }
                }
                var tab by remember { mutableStateOf(Tab.CONVERT) }
                var tourOpen by remember {
                    mutableStateOf(!TourPrefs.seen(applicationContext))
                }
                // The update checker: created once, checked quietly
                // on launch (its own rules decide whether to run),
                // and shared with the About tab and the dialog.
                val updateController = remember {
                    UpdateController(applicationContext)
                }
                LaunchedEffect(Unit) { updateController.maybeAutoCheck() }
                // Floating chrome: content fills the screen and scrolls
                // under a floating navigation pill, instead of living
                // between two fixed bars. The screens pad their own
                // content clear of the pill.
                // The root Surface also provides the theme's content
                // color, without it, plain text falls back to black.
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
                            (fadeIn(tween(180)) +
                                slideInHorizontally(tween(180)) { it / 6 * direction })
                                .togetherWith(
                                    fadeOut(tween(120)) +
                                        slideOutHorizontally(tween(180)) {
                                            -it / 6 * direction
                                        },
                                )
                        },
                        label = "tab",
                    ) { currentTab ->
                        when (currentTab) {
                            Tab.CONVERT -> ConvertScreen(
                                reportState.value,
                                Modifier.fillMaxSize().statusBarsPadding(),
                                onStartPressed = { tab = Tab.QUEUE },
                            )
                            Tab.QUEUE -> QueueScreen(
                                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            )
                            Tab.HARDWARE -> CapabilityScreen(reportState.value, Modifier.fillMaxSize().statusBarsPadding())
                            Tab.SETTINGS -> SettingsScreen(
                                template = template,
                                mode = mode,
                                onTemplateChange = { update(it, mode) },
                                onModeChange = { update(template, it) },
                                languageTag = languageTag,
                                onLanguageChange = { changeLanguage(it) },
                                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            )
                            Tab.ABOUT -> AboutScreen(
                                updateController = updateController,
                                onReplayTour = { tourOpen = true },
                                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            )
                        }
                    }
                    FloatingNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    if (tourOpen) {
                        FeatureTour(
                            currentTab = tab,
                            onSelectTab = { tab = it },
                            onFinish = {
                                TourPrefs.markSeen(applicationContext)
                                tourOpen = false
                            },
                        )
                    }
                    UpdateDialog(updateController)
                }
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
     * can still override it, nothing app-side can outrank that, and
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

/**
 * The floating navigation pill: all five tabs in a horizontally
 * sweepable row. The pill's width is fixed by the screen, never by
 * its content, a selected tab's expanding label used to widen the
 * whole pill, push the last tab off the screen's edge, and (when
 * the label wrapped mid-animation) balloon the pill's height before
 * dropping it back. Now the pill cannot resize at all: anything
 * that doesn't fit is a swipe away inside the pill, and selecting
 * a tab scrolls it fully into view on its own.
 */
@Composable
private fun FloatingNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem(selected.ordinal)
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .floatingSurface(shape)
            .clip(shape),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(Tab.entries, key = { it.name }) { entry ->
            NavPillItem(
                entry = entry,
                selected = entry == selected,
                onClick = { onSelect(entry) },
                tourKey = when (entry) {
                    Tab.QUEUE -> TourKeys.NAV_QUEUE
                    Tab.SETTINGS -> TourKeys.NAV_SETTINGS
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun NavPillItem(
    entry: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    tourKey: String? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    // One motion language for the whole switch: a single 200 ms tween.
    // The label used to expand on AnimatedVisibility's default spring,
    // whose settle tail read as a stutter, while the colors snapped
    // instantly beside it, now everything moves together, once.
    val background by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f)
        },
        animationSpec = tween(200),
        label = "navItemBackground",
    )
    val contentColor by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "navItemContent",
    )
    Row(
        modifier = Modifier
            .then(if (tourKey != null) Modifier.tourTarget(tourKey) else Modifier)
            .height(48.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            entry.icon,
            contentDescription = stringResource(entry.titleRes),
            tint = contentColor,
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200)),
        ) {
            // One line, never wrapping: during the expand animation
            // the label's width is clipped, and a wrapping label
            // measured two lines tall mid-animation, the item (and
            // the whole pill with it) jumped up, then dropped back
            // when the animation finished and the text unwrapped.
            Text(
                stringResource(entry.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
