package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.barShadow
import app.aroma.ui.theme.extras

/** The height of [BottomActionBar] above the system navigation. */
val ActionBarHeight: Dp = 72.dp

/** How much of the window's foot a [BottomActionBar] takes, the system navigation included. */
@Composable
fun actionBarInset(): Dp = ActionBarHeight + with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

/**
 * The bar at the foot of a page whose main action stays at hand while the page scrolls: icon
 * buttons on the left, the page's one primary button across the rest. It rests on the bottom edge,
 * down over the system navigation, and casts its soft shadow up onto the page.
 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .barShadow()
                .background(extras.raised)
                .navigationBarsPadding()
                .height(ActionBarHeight)
                .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        content = content,
    )
}

/**
 * A round icon button of a bar, on the soft fill. [confirmed] turns its glyph into a check for a
 * moment once its action went through.
 */
@Composable
fun BarIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    confirmed: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .size(TouchTarget)
                .clip(CircleShape)
                .background(extras.card)
                .clickable(role = Role.Button, onClickLabel = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(confirmed, animationSpec = tween(Motion.STATE), label = "barIcon") { done ->
            Icon(
                painter = painterResource(if (done) R.drawable.ic_check else icon),
                contentDescription = contentDescription,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Glyph.m),
            )
        }
    }
}
