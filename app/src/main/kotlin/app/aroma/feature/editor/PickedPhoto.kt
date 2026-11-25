// From Android 8 on, the platform's ExifInterface reads a photo's orientation as well as androidx's,
// which is not among Aroma's libraries.
@file:Suppress("ExifInterface")

package app.aroma.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max

/** A photo from the picker, ready to upload. */
class PickedPhoto(val bytes: ByteArray, val extension: String)

/**
 * Reads a picked photo off the main thread. Camera photos are scaled down to what a recipe page
 * can show, so uploads stay small and fast; Mealie stores its own smaller copies anyway.
 */
suspend fun readPickedPhoto(context: Context, uri: Uri): PickedPhoto? =
    withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        if (scaleDown({ context.contentResolver.openInputStream(uri) }, out)) PickedPhoto(out.toByteArray(), "jpg") else null
    }

/** Writes a picked photo into [target], scaled down as [readPickedPhoto] does it; false when it cannot be read. */
suspend fun savePickedPhoto(context: Context, uri: Uri, target: File): Boolean =
    withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val saved =
            try {
                target.outputStream().use { scaleDown({ context.contentResolver.openInputStream(uri) }, it) }
            } catch (_: IOException) {
                false
            }
        if (!saved) target.delete()
        saved
    }

/**
 * Writes the photo [open] reads as a JPEG of at most [MAX_EDGE] on its longest side, upright as
 * the camera was held. It is decoded at the smallest power-of-two fraction of its size that still
 * covers [MAX_EDGE], so a photo of many megapixels never fills the memory. False when it cannot be read.
 */
internal fun scaleDown(open: () -> InputStream?, out: OutputStream): Boolean {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return false
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (decoded == null) return false
        val factor = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        val transform = upright(open).apply { if (factor < 1f) postScale(factor, factor) }
        val photo = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if (photo !== decoded) decoded.recycle()
        val written = photo.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        photo.recycle()
        return written
    } catch (_: IOException) {
        return false
    } catch (_: SecurityException) {
        return false
    }
}

/**
 * Turns a photo upright: cameras save what the sensor saw and note in the photo's EXIF data how
 * they were held, as a mirroring and then a turn clockwise. Decoding ignores the note, and the
 * JPEG written afterwards has none.
 */
private fun upright(open: () -> InputStream?): Matrix {
    val orientation =
        try {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: IOException) {
            null
        }
    val degrees =
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE -> 90f
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE -> 270f
            else -> 0f
        }
    val mirrored =
        orientation in
            setOf(
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
                ExifInterface.ORIENTATION_FLIP_VERTICAL,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_TRANSVERSE,
            )
    return Matrix().apply {
        if (mirrored) postScale(-1f, 1f)
        postRotate(degrees)
    }
}

/**
 * A new place in the cache for the camera app to save a photo into, as a uri it may write to. The
 * app's one file provider, declared for downloaded updates, serves the camera's folder too.
 */
fun newCameraPhoto(context: Context): Uri {
    val file = File(context.cacheDir, "$CAMERA_FOLDER/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, filesAuthority(context), file)
}

/** Removes a photo the camera app saved for Aroma; picked photos are someone else's and stay. */
fun forgetCameraPhoto(context: Context, uri: Uri) {
    if (uri.authority == filesAuthority(context)) context.contentResolver.delete(uri, null, null)
}

/** Removes every photo the camera app saved for Aroma, as when the user signs out. */
fun forgetCameraPhotos(context: Context) {
    File(context.cacheDir, CAMERA_FOLDER).deleteRecursively()
}

private fun filesAuthority(context: Context) = "${context.packageName}.updates"

private const val CAMERA_FOLDER = "camera"
private const val MAX_EDGE = 2048
private const val QUALITY = 85
