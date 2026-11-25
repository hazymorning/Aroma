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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
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
 * under it ([raised]). Its paper fades in with its title, so what lies under it fades out rather
 * than vanishing at once. Before that it is only the status bar's ground, the page's paper, with the
 * soft edge under it that content scrolls into ([scrolled], see [StatusBarEdge]). [navigation]
 * stands left of the title, [actions] on the right.
 */
@Composable
fun TopBar(
    title: String,
    collapsed: Boolean,
    raised: Boolean,
    modifier: Modifier = Modifier,
    scrolled: () -> Float = { if (raised) Float.POSITIVE_INFINITY else 0f },
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AromaTheme.colors
    // Read while drawing only, so the head fades without composing again on every frame.
    val shown = animateFloatAsState(if (collapsed) 1f else 0f, Motion.edge(), label = "topBar")
    val lifted = animateFloatAsState(if (collapsed && raised) 1f else 0f, Motion.edge(), label = "topBarShadow")
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Column(
        modifier
            .fillMaxWidth()
            .layeredShadow(AromaTheme.shadows.topBar) { lifted.value }
            .drawWithCache {
                val edge = scrollEdge(colors.background, statusBar)
                onDrawBehind {
                    // The status bar's ground turns from the page's paper to the bar's as the head comes.
                    drawRect(lerp(colors.background, colors.bar, shown.value), size = Size(size.width, statusBar))
                    edge.draw(this, (1f - shown.value) * edge.share(scrolled()))
                }
            },
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        AnimatedVisibility(collapsed, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
            CompositionLocalProvider(LocalTonalFill provides colors.surfaceOnBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(TopBarHeight)
                        .background(colors.bar)
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
 * The top of a page that scrolls beneath the status bar and has no head of its own there: the
 * page's paper behind the clock and the icons, and under it a soft edge into which the content
 * fades as it scrolls up ([scrolled], in pixels, read while drawing), instead of being cut by a
 * line. At rest the edge is not there, so it never lies over the page's own head.
 */
@Composable
fun StatusBarEdge(scrolled: () -> Float, modifier: Modifier = Modifier) {
    val paper = AromaTheme.colors.background
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Spacer(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .drawWithCache {
                val edge = scrollEdge(paper, statusBar)
                onDrawBehind {
                    drawRect(paper)
                    edge.draw(this, edge.share(scrolled()))
                }
            },
    )
}

/** How far [this] list has moved its content up beneath the top of the page, in pixels; past its first item, as far as can be. */
fun LazyListState.scrolledBeneath(): Float {
    if (firstVisibleItemIndex > 0) return Float.POSITIVE_INFINITY
    return firstVisibleItemScrollOffset.toFloat()
}

/** The soft edge under the status bar, from the page's [paper] at [top] to nothing [Motion.ScrollEdge] lower. */
private fun CacheDrawScope.scrollEdge(paper: Color, top: Float): ScrollEdgeFade {
    val height = Motion.ScrollEdge.toPx()
    // A smooth step from full paper to none, so neither end of the edge shows as a line.
    val stops =
        Array(EDGE_STOPS + 1) { index ->
            val at = index / EDGE_STOPS.toFloat()
            at to paper.copy(alpha = 1f - at * at * (3f - 2f * at))
        }
    return ScrollEdgeFade(Brush.verticalGradient(*stops, startY = top, endY = top + height), top, height)
}

/** How finely the edge's fade is set. */
private const val EDGE_STOPS = 8

private class ScrollEdgeFade(private val brush: Brush, private val top: Float, private val height: Float) {
    /** How much of the edge shows once the content has moved [scrolled] pixels: all of it once it has moved the edge's height. */
    fun share(scrolled: Float): Float = (scrolled / height).coerceIn(0f, 1f)

    fun draw(scope: DrawScope, alpha: Float) {
        if (alpha > 0f) scope.drawRect(brush, topLeft = Offset(0f, top), size = Size(scope.size.width, height), alpha = alpha)
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
            scrolled = { scroll.value.toFloat() },
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
