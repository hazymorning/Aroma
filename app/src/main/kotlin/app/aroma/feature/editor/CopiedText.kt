package app.aroma.feature.editor

import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text on the clipboard, as its description tells it: whether it is a web link, and when it was copied ([at], wall-clock time). */
data class Copied(val isLink: Boolean, val at: Long)

/**
 * Tells [onChange] what the clipboard holds each time Aroma's window gains the focus, as when the
 * user comes back from copying a link elsewhere: Android shows the clipboard only to the app in focus.
 */
@Composable
fun WatchClipboard(onChange: (Copied?) -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(focused) {
        if (focused) change(clipboard.copied(context))
    }
}

/** The text on the clipboard, read when the user asks to paste it. */
suspend fun Clipboard.copiedText(context: Context): String? =
    withContext(Dispatchers.IO) {
        getClipEntry()
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    }

/**
 * The clip on the clipboard, told by its description alone: from Android 12 on, Android tells the
 * user each time an app reads a clip itself, but it has marked in the description whether the
 * text is a link. Before, reading the text goes unnoticed. Text marked sensitive, such as a
 * password, is left alone.
 */
private suspend fun Clipboard.copied(context: Context): Copied? {
    val description = nativeClipboard.primaryClipDescription ?: return null
    if (!description.hasMimeType("text/*") || description.extras?.getBoolean(IS_SENSITIVE) == true) return null
    val isLink =
        when {
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> description.isLink()
            else -> copiedText(context)?.let(::findLink) != null
        }
    return Copied(isLink, description.timestamp)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun ClipDescription.isLink(): Boolean =
    classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE && getConfidenceScore(TextClassifier.TYPE_URL) >= LINK_CONFIDENCE

/** How sure Android must be that the copied text is a link: more likely than not. */
private const val LINK_CONFIDENCE = 0.5f

/** [ClipDescription.EXTRA_IS_SENSITIVE], which apps set on every version but Android names only from 13 on. */
private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
