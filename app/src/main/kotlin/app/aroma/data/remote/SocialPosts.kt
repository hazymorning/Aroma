package app.aroma.data.remote

import app.aroma.core.format.LinkPreview
import app.aroma.core.format.SocialCaption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A social media post as its link preview shows it. */
data class SocialPost(val caption: String, val image: String?)

/** A photo as downloaded, with the file extension Mealie stores it under. */
class Photo(val bytes: ByteArray, val extension: String)

/**
 * Reads social media posts on the phone, from their link preview, as messengers do. Instagram and
 * similar platforms show a post's caption there and nowhere else a recipe importer could read it.
 * Nothing of the Mealie session goes along: the requests leave without the app's interceptors.
 */
class SocialPosts(client: OkHttpClient, private val userAgent: () -> String) {
    private val http =
        client
            .newBuilder()
            .apply { interceptors().clear() }
            .callTimeout(READ_SECONDS, TimeUnit.SECONDS)
            .build()

    /** The post at [url], or null when the platform shows it only to signed-in users. */
    suspend fun read(url: String): SocialPost? =
        withContext(Dispatchers.IO) {
            val request = request(url) ?: return@withContext null
            try {
                http.newCall(request).execute().use { response ->
                    // Platforms send visitors they do not let in to their sign-in page.
                    if (!response.isSuccessful || "login" in response.request.url.encodedPath) return@withContext null
                    val source = response.body.source()
                    source.request(MAX_PAGE_BYTES)
                    val preview = LinkPreview.parse(source.buffer.readUtf8(minOf(source.buffer.size, MAX_PAGE_BYTES)))
                    val caption = SocialCaption.extract(preview.title, preview.description)
                    if (caption.isBlank() || SocialCaption.isSignInPage(preview.title, preview.description)) {
                        null
                    } else {
                        SocialPost(caption, preview.image)
                    }
                }
            } catch (_: IOException) {
                null
            }
        }

    /** The photo at [url], or null if it cannot be loaded or is no photo. */
    suspend fun photo(url: String): Photo? =
        withContext(Dispatchers.IO) {
            val request = request(url) ?: return@withContext null
            try {
                http.newCall(request).execute().use { response ->
                    val type = response.body.contentType()
                    if (!response.isSuccessful || type?.type != "image") return@withContext null
                    val source = response.body.source()
                    // A photo larger than this is no post's photo, and would not fit in memory either.
                    if (source.request(MAX_PHOTO_BYTES + 1)) return@withContext null
                    Photo(source.buffer.readByteArray(), if (type.subtype == "jpeg") "jpg" else type.subtype)
                }
            } catch (_: IOException) {
                null
            }
        }

    private fun request(url: String): Request? {
        val address = url.toHttpUrlOrNull() ?: return null
        return Request
            .Builder()
            .url(address)
            // A device name outside ASCII would make the header invalid.
            .header("User-Agent", userAgent().filter { it in ' '..'~' })
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()
    }

    companion object {
        private const val READ_SECONDS = 15L

        /** The link preview sits in a page's head; nothing after this many bytes is read. */
        private const val MAX_PAGE_BYTES = 2L * 1024 * 1024

        private const val MAX_PHOTO_BYTES = 16L * 1024 * 1024

        private val Hosts = listOf("instagram.com", "facebook.com", "fb.watch", "threads.net", "threads.com", "tiktok.com")

        /** Posts of the platforms that show recipes only in a caption or a video. */
        fun isPost(url: String): Boolean {
            val host = url.trim().toHttpUrlOrNull()?.host ?: return false
            return Hosts.any { host == it || host.endsWith(".$it") }
        }
    }
}
