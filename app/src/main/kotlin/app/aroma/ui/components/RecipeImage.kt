package app.aroma.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * outline inside its edge keeps a light photo from melting into the paper. Recipes without a photo,
 * or with one that will not load, keep the fill with a plate on it. With [placeholder], a smaller
 * copy already in memory, from a card, stands in until [size] arrives.
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
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(colors.surfacePressed)
                .drawWithContent {
                    drawContent()
                    // Twice as wide as shown: the outer half falls outside the clip, the inner pixel stays.
                    drawOutline(shape.createOutline(this.size, layoutDirection, this), outline, style = Stroke(width = 2f))
                },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null || failed) {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(fallbackIconSize),
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

/**
 * How fast the system runs animations, 0 with them turned off. Compose's own animations follow it by
 * themselves; Coil times its crossfade on its own clock, so the photo is told.
 */
@Composable
private fun systemAnimationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
}
