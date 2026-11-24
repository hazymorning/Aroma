package app.aroma.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * A recipe photo at [size] in Mealie's [rendition], its small one unless asked, through the app's
 * image loader and its cache, so a photo seen in the app shows offline too. Software bitmaps
 * without alpha, the lightest a launcher can be handed; null when it does not arrive in time.
 */
internal suspend fun Context.recipePhoto(image: RecipeImage, size: Dp, rendition: ImageSize = ImageSize.Thumbnail): Bitmap? =
    withTimeoutOrNull(PHOTO_PATIENCE) {
        val request =
            ImageRequest
                .Builder(this@recipePhoto)
                .data(image.url(rendition))
                .size((size.value * resources.displayMetrics.density).roundToInt())
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowHardware(false)
                .build()
        (SingletonImageLoader.get(this@recipePhoto).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

private val PHOTO_PATIENCE = 5.seconds
