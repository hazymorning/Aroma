package app.aroma.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Radius

/** The shared transition around the screens, which carries a recipe's photo and name from one screen to the next. */
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The screen's own coming and going, which the photo and the name follow. */
val LocalScreenTransition = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** What of a recipe travels from its card or row to its page. */
enum class RecipePart { Photo, Name }

/**
 * Carries this part of the recipe [slug] from where it stands to where it stands on the next
 * screen: the photo grows from the card into the page's photo, the name moves with it, and back
 * again, over the 350 ms of the page change. Without a [slug], in a sheet, or with the system's
 * animations turned off, it stays where it is.
 */
@Composable
fun Modifier.sharedRecipe(part: RecipePart, slug: String?): Modifier {
    val shared = LocalSharedTransition.current
    val screen = LocalScreenTransition.current
    if (slug == null || shared == null || screen == null || reducedMotion()) return this
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val corners = remember(windowWidth) { GrowingCorners(Radius.cardImage, windowWidth.toFloat()) }
    val modifier = this
    return with(shared) {
        val state = rememberSharedContentState("${part.name}:$slug")
        when (part) {
            RecipePart.Photo -> {
                modifier.sharedElement(state, screen, boundsTransform = Travel, clipInOverlayDuringTransition = corners)
            }

            // The name changes its size on the way, from a card's to a title's: the old one fades out
            // as it sets off, the new one in once it has gone, so the two never stand on each other.
            RecipePart.Name -> {
                modifier.sharedBounds(
                    state,
                    screen,
                    enter = fadeIn(tween(Motion.PAGE - Motion.EDGE, delayMillis = Motion.EDGE)),
                    exit = fadeOut(tween(Motion.EDGE)),
                    boundsTransform = Travel,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                )
            }
        }
    }
}

/** The page change's duration and curve, for what travels with it. */
private val Travel = BoundsTransform { _, _ -> Motion.page() }

/**
 * A photo's corners on the way: a card's round corners while it is small, square as it reaches the
 * window's width, as the page's photo is. Only while it travels; at rest each photo has its own.
 */
private class GrowingCorners(private val radius: Dp, private val windowWidth: Float) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val grown = if (windowWidth > 0f) bounds.width / windowWidth else 1f
        val corner = with(density) { radius.toPx() } * ((1f - grown) * 2f).coerceIn(0f, 1f)
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(corner)))
        return path
    }
}

/** Whether the system asks for no motion: its animations turned off, as "Remove animations" does. */
@Composable
fun reducedMotion(): Boolean = systemAnimationScale() == 0f

/**
 * How fast the system runs animations, 0 with them turned off. Compose's own animations follow it by
 * themselves; Coil times its crossfade on its own clock, so the photo is told.
 */
@Composable
internal fun systemAnimationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
}
