package app.aroma.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.HeaderTop
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.NavigationGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.SubtitleGap
import app.aroma.ui.theme.layeredShadow

/** The height of the collapsed head, below the status bar. */
val TopBarHeight: Dp = 56.dp

/**
 * Whether a page's large head has scrolled away: [LargeHeader] tells it, and the [TopBar] shows the
 * title once it has. At rest the large title stands where the collapsed head would; it slides up
 * beneath the status bar, and once it has gone there the collapsed head takes its place.
 */
@Stable
class HeaderState {
    var collapsed by mutableStateOf(false)
        internal set

    /** Where the status bar ends, in pixels from the window's top. */
    internal var statusBarBottom = 0f
}

@Composable
fun rememberHeaderState(): HeaderState {
    val state = remember { HeaderState() }
    val density = LocalDensity.current
    state.statusBarBottom = WindowInsets.statusBars.getTop(density).toFloat()
    return state
}

/** Tells [state] when the title it is set on has scrolled away beneath the status bar. */
fun Modifier.collapsesInto(state: HeaderState): Modifier =
    onGloballyPositioned { coordinates ->
        val gone = coordinates.boundsInRoot().bottom <= state.statusBarBottom
        if (gone != state.collapsed) state.collapsed = gone
    }

/**
 * The large head of a page: its title in display type, a [subtitle] line under it where there is
 * one, and up to two icon buttons on the right, centred on the title's first line. It stands 16 dp
 * below the status bar on the page's own paper; [navigation], a back button, stands above it on a
 * pushed page, in the row the collapsed head will have, 16 dp above the title. The content starts
 * 24 dp below it.
 */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    state: HeaderState? = null,
    subtitle: (@Composable () -> Unit)? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val lineHeight =
        with(LocalDensity.current) {
            AromaTheme.type.display.lineHeight
                .toDp()
        }
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        if (navigation != null) {
            Box(
                Modifier
                    .height(TopBarHeight)
                    .padding(start = PageMargin - IconButtonReach),
                contentAlignment = Alignment.CenterStart,
            ) { navigation() }
            Spacer(Modifier.height(NavigationGap))
        } else {
            Spacer(Modifier.height(HeaderTop))
        }
        Row(Modifier.padding(start = PageMargin, end = PageMargin - IconButtonReach)) {
            Text(
                text = title,
                style = AromaTheme.type.display,
                modifier =
                    Modifier
                        .weight(1f)
                        .then(if (state != null) Modifier.collapsesInto(state) else Modifier)
                        .semantics { heading() },
            )
            // The buttons centre on the title's first line, whatever it runs to.
            Row(
                Modifier
                    .padding(start = Space.s8)
                    .height(lineHeight)
                    .wrapContentHeight(unbounded = true),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        if (subtitle != null) {
            Box(Modifier.padding(start = PageMargin, end = PageMargin, top = SubtitleGap)) { subtitle() }
        }
    }
}

/** The line under a page's title, in the subtitle role. */
@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AromaTheme.type.subtitle, modifier = modifier)
}

/**
 * The collapsed head: 56 dp of bar paper under the status bar with the page's title in one line,
 * there once the large head has scrolled beneath it ([collapsed]), with its shadow once content lies
 * under it ([raised]). Before that it is only the status bar's ground, the page's paper.
 * [navigation] stands left of the title, [actions] on the right.
 */
@Composable
fun TopBar(
    title: String,
    collapsed: Boolean,
    raised: Boolean,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AromaTheme.colors
    val shadow by animateFloatAsState(if (collapsed && raised) 1f else 0f, Motion.edge(), label = "topBarShadow")
    val ground by androidx.compose.animation.animateColorAsState(
        if (collapsed) colors.bar else colors.background,
        Motion.edge(),
        label = "topBarGround",
    )
    Column(
        modifier
            .fillMaxWidth()
            .layeredShadow(AromaTheme.shadows.topBar, alpha = shadow)
            .background(ground),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        AnimatedVisibility(collapsed, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
            androidx.compose.runtime.CompositionLocalProvider(LocalTonalFill provides colors.surfaceOnBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(TopBarHeight)
                        .padding(start = if (navigation != null) PageMargin - IconButtonReach else PageMargin)
                        .padding(end = PageMargin - IconButtonReach),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (navigation != null) {
                        navigation()
                        Spacer(Modifier.width(Space.s4))
                    }
                    Text(
                        text = title,
                        style = AromaTheme.type.barTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    actions()
                }
            }
        }
    }
}

/**
 * A pushed page: the back button, the large title and the [content] in one scrolling column no
 * wider than [ContentMaxWidth]; once the title has scrolled beneath the collapsed head, the head
 * takes it up.
 */
@Composable
fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val header = rememberHeaderState()
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                // With a bar, the page ends above it: nothing can scroll out of reach beneath it.
                .then(if (bottomBar != null) Modifier.padding(bottom = actionBarInset()) else Modifier)
                .verticalScroll(scroll)
                .then(if (bottomBar != null) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = EndSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                LargeHeader(
                    title = title,
                    state = header,
                    navigation = { BackButton(onBack) },
                )
                Spacer(Modifier.height(HeaderGap))
                content()
            }
        }
        TopBar(
            title = title,
            collapsed = header.collapsed,
            raised = scroll.canScrollBackward,
            navigation = { BackButton(onBack) },
        )
        if (bottomBar != null) Box(Modifier.align(Alignment.BottomCenter).imePadding()) { bottomBar() }
    }
}

/** The back button of a pushed page. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, modifier)
}

/**
 * A section's title in the section role, with an optional text action at its end, "Alle". Its
 * content follows 12 dp below; sections stand 40 dp apart.
 */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AromaTheme.type.section,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(Space.s16))
            // The action's 48 dp target reaches above and below the title's line without making the row taller.
            Box(Modifier.height(SectionActionHeight).wrapContentHeight(unbounded = true)) { TextAction(actionLabel, onAction) }
        }
    }
}

private val SectionActionHeight = 28.dp

/**
 * The ground of the status bar on a page that scrolls beneath it: the page's own paper, so nothing
 * runs under the clock and the icons. A page with a photo at its head draws its photo there instead.
 */
@Composable
fun StatusBarCover(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(AromaTheme.colors.background)
            .windowInsetsTopHeight(WindowInsets.statusBars),
    )
}

/**
 * Light status bar icons while [overPhoto], as the shaded top of a photo needs them, and the
 * theme's own once the photo has gone or the page leaves.
 */
@Composable
fun StatusBarOverPhoto(overPhoto: Boolean) {
    val view = LocalView.current
    val dark = AromaTheme.colors.isDark
    DisposableEffect(view, overPhoto, dark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !overPhoto && !dark
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
