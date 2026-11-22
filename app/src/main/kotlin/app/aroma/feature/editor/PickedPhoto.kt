package app.aroma.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/** A photo from the picker, ready to upload. */
class PickedPhoto(val bytes: ByteArray, val extension: String)

/**
 * Reads a picked photo off the main thread. Camera photos are scaled down to what a recipe page
 * can show, so uploads stay small and fast; Mealie stores its own smaller copies anyway.
 */
suspend fun readPickedPhoto(context: Context, uri: Uri): PickedPhoto? =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scaled(context, uri) else original(context, uri)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun scaled(context: Context, uri: Uri): PickedPhoto {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    // ImageDecoder applies the camera's rotation, which a plain re-encode would otherwise lose.
    val bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val factor = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
    bitmap.recycle()
    return PickedPhoto(out.toByteArray(), "jpg")
}

private fun original(context: Context, uri: Uri): PickedPhoto? {
    val type = context.contentResolver.getType(uri).orEmpty()
    val extension =
        when {
            type.endsWith("png") -> "png"
            type.endsWith("webp") -> "webp"
            else -> "jpg"
        }
    return context.contentResolver.openInputStream(uri)?.use { PickedPhoto(it.readBytes(), extension) }
}

private const val MAX_EDGE = 2048
private const val QUALITY = 88
