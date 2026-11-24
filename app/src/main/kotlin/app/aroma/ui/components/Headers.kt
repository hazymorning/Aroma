package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget

/**
 * The head of a tab page: a large serif title on the margin and plain icon actions whose last glyph
 * ends on the right margin. The actions stand centred beside the title; [actions] learns whether it
 * runs over two lines.
 */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.(tall: Boolean) -> Unit = {}) {
    var titleLines by remember { mutableIntStateOf(1) }
    val tall = titleLines > 1
    // A title of one line stands at the same height on every tab, with or without actions: they
    // centre on its line without making the header taller. Beside two lines they centre on both.
    val lineHeight = with(LocalDensity.current) { AromaType.PageTitle.lineHeight.toDp() }
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = ScreenMargin, end = EdgeActionPadding, top = Space.l),
        verticalAlignment = if (tall) Alignment.CenterVertically else Alignment.Top,
    ) {
        Text(
            text = title,
            style = AromaType.PageTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLines = it.lineCount },
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Row(
            modifier =
                if (tall) {
                    Modifier
                } else {
                    Modifier
                        .height(lineHeight)
                        .wrapContentHeight(unbounded = true)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) { actions(tall) }
    }
}

/** The padding before an icon action at the edge of a bar, so its glyph, not its target, ends on the margin. */
val EdgeActionPadding: Dp = ScreenMargin - (TouchTarget - Glyph.l) / 2

/** A plain 48 dp icon action for headers and bars, with a 24 dp glyph in ink or in [tint]. */
@Composable
fun HeaderAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconAction(icon, contentDescription, onClick, modifier, tint = tint, glyphSize = Glyph.l)
}

/**
 * The bar of a pushed screen: back on the left (its arrow lands on the margin), actions on the
 * right. The large title lives in the content; once it has scrolled away ([collapsed]), a small
 * title and a hairline fade in.
 */
@Composable
fun DetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .padding(horizontal = EdgeActionPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = collapsed && title != null,
                    enter = fadeIn(tween(Motion.STATE)),
                    exit = fadeOut(tween(Motion.STATE)),
                ) {
                    Text(
                        text = title.orEmpty(),
                        style = AromaType.Name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Space.xs),
                    )
                }
            }
            actions()
        }
        AnimatedVisibility(collapsed, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) { Hairline() }
    }
}

/** Height of [DetailTopBar] below the status bar, for content that scrolls beneath it. */
val DetailTopBarHeight = 56.dp

/** The large title of a pushed screen, set in the content under [DetailTopBar]. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AromaType.PageTitle,
        modifier =
            modifier
                .padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs)
                .semantics { heading() },
    )
}

/**
 * A pushed page: a large title and a scrolling column under [DetailTopBar], no wider than
 * [ContentMaxWidth]. Once the title has scrolled beneath the bar, the bar shows it in small.
 */
@Composable
fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var titleEnd by remember { mutableIntStateOf(Int.MAX_VALUE) }
    // The bar takes up the title once half of it has gone beneath, as the large title fades away.
    val collapsed by remember(scroll) { derivedStateOf { scroll.value > titleEnd / 2 } }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(top = DetailTopBarHeight)
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                LargeTitle(
                    title,
                    Modifier
                        .onSizeChanged { titleEnd = it.height }
                        .graphicsLayer { alpha = largeTitleAlpha(scroll.value, titleEnd) },
                )
                content()
            }
        }
        DetailTopBar(onBack = onBack, title = title, collapsed = collapsed, actions = actions)
    }
}

/**
 * How much of a large title [titleEnd] px tall still shows once the page has scrolled [scrolled] px:
 * it fades out as it passes under the bar, before the bar takes up the title.
 */
fun largeTitleAlpha(scrolled: Int, titleEnd: Int): Float = (1f - scrolled / (titleEnd * TITLE_FADE)).coerceIn(0f, 1f)

/** How much of a large title has scrolled away by the time it has faded out. */
private const val TITLE_FADE = 0.6f

/** Keeps the status bar legible over content scrolled beneath it, by fading the page in from the top. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(page, page.copy(alpha = 0f))))
            .statusBarsPadding()
            .height(Space.m),
    )
}
