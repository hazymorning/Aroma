package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Recipe photo with a calm fallback for recipes without an image or while offline. With
 * [placeholder], a smaller copy already in memory (from a list card) shows until [size] arrives.
 */
@Composable
fun RecipeImage(
    image: RecipeImage?,
    size: ImageSize,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    fallbackIconSize: Dp = 32.dp,
    placeholder: ImageSize? = null,
) {
    var failed by remember(image) { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null || failed) {
            Icon(
                painter = painterResource(R.drawable.ic_restaurant_menu),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
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
                        .crossfade(true)
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
