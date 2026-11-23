package app.aroma.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin

/**
 * The head of a tab page: an optional kicker, a large serif title (with an optional second line in
 * sage, as in the greeting) and plain icon actions whose last glyph ends on the right margin.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    accent: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = ScreenMargin, end = 8.dp, top = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (kicker != null) {
                Kicker(kicker)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (accent != null) {
                Text(
                    text = accent,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(Modifier.offset(y = if (kicker != null) 22.dp else (-2).dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A plain 48 dp icon action for headers and bars, with a 24 dp ink glyph. */
@Composable
fun HeaderAction(icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The bar of a pushed screen: back on the left (its arrow lands on the 24 dp margin), actions on the
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
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .padding(horizontal = 8.dp),
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
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
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
fun LargeTitle(text: String, modifier: Modifier = Modifier, bottomSpacing: Dp = 24.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        modifier =
            modifier
                .padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp, bottom = bottomSpacing)
                .semantics { heading() },
    )
}

/** Keeps the status bar legible over content scrolled beneath it, by fading the page in from the top. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(page, page.copy(alpha = 0f))))
            .statusBarsPadding()
            .height(16.dp),
    )
}
