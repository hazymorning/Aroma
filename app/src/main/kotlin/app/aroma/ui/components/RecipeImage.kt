package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeImage
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt

/**
 * A recipe's photo: a quiet fill while it loads, then the photo fading in over 200 ms. A fine
 * outline inside its edge keeps a light photo from melting into the paper. Recipes without a photo
 * keep the fill with a plate on it; where a photo will not load, the plate fades in as the photo
 * would have. With [placeholder], a smaller copy already in memory, from a card, stands in until
 * [size] arrives.
 */
@Composable
fun RecipeImage(
    image: RecipeImage?,
    size: ImageSize,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    fallbackIconSize: Dp = 28.dp,
    @DrawableRes fallbackIcon: Int = R.drawable.ic_plate,
    placeholder: ImageSize? = null,
) {
    var failed by remember(image) { mutableStateOf(false) }
    val colors = AromaTheme.colors
    val fadeIn = (Motion.IMAGE * systemAnimationScale()).roundToInt()
    // Ink at 6 % on paper; on dark photos in the dark, white at 6 %.
    val outline = if (colors.isDark) Color.White.copy(alpha = OUTLINE) else colors.ink.copy(alpha = OUTLINE)
    // A photo that is not there to begin with has its plate at once; one that fails, after a fade.
    val plate = remember(image) { Animatable(if (image == null) 1f else 0f) }
    LaunchedEffect(failed) { if (failed) plate.animateTo(1f, tween(Motion.IMAGE, easing = Motion.Decelerate)) }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(colors.surfacePressed)
                .drawWithCache {
                    val edge = shape.createOutline(this.size, layoutDirection, this)
                    // Twice as wide as shown: the outer half falls outside the clip, the inner pixel stays.
                    val stroke = Stroke(width = 2f)
                    onDrawWithContent {
                        drawContent()
                        drawOutline(edge, outline, style = stroke)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null || failed) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier =
                    Modifier
                        .size(fallbackIconSize)
                        .graphicsLayer { alpha = plate.value },
            )
        }
        if (image != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(image.url(size))
                        // Keyed by URL alone, so a card's copy can stand in on the next screen.
                        .memoryCacheKey(image.url(size))
                        .placeholderMemoryCacheKey(placeholder?.let(image::url))
                        .crossfade(fadeIn)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                onSuccess = { failed = false },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** How strong the outline inside a photo's edge is. */
private const val OUTLINE = 0.06f
