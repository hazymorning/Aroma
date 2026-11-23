package app.aroma.data.remote

import app.aroma.core.format.InstagramEmbed
import app.aroma.core.format.LinkPreview
import app.aroma.core.format.SocialCaption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
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
 * Reads social media posts on the phone. An Instagram post comes from its embed, which Instagram
 * shows other websites' visitors without signing in; any post, Instagram's too where its embed
 * fails, from its link preview, as messengers read it with the phone's browser as [userAgent].
 * Nothing of the Mealie session goes along: the requests leave without the app's interceptors.
 * [appAgent] names Aroma itself, which a platform's embed answers with the caption in the page.
 */
class SocialPosts(client: OkHttpClient, private val appAgent: String = "Aroma", private val userAgent: () -> String) {
    private val http =
        client
            .newBuilder()
            .apply { interceptors().clear() }
            .callTimeout(READ_SECONDS, TimeUnit.SECONDS)
            .build()

    /** The post at [url], or null when the platform shows it only to signed-in users. */
    suspend fun read(url: String): SocialPost? =
        withContext(Dispatchers.IO) {
            val address = url.trim().toHttpUrlOrNull() ?: return@withContext null
            embedded(address) ?: linkPreview(address)
        }

    /** An Instagram post from its embed, which answers in about a second, caption and photo in the page. */
    private fun embedded(address: HttpUrl): SocialPost? {
        if (address.host != INSTAGRAM && !address.host.endsWith(".$INSTAGRAM")) return null
        val code = InstagramEmbed.code(address.encodedPath) ?: shared(address)?.let(InstagramEmbed::code) ?: return null
        val embed =
            address
                .newBuilder()
                .encodedPath("/p/$code/embed/captioned/")
                .query(null)
                .fragment(null)
                .build()
        val html = page(embed, appAgent) ?: return null
        val caption = InstagramEmbed.caption(html) ?: return null
        return SocialPost(caption, InstagramEmbed.photo(html))
    }

    /**
     * Where a share link such as instagram.com/share/reel/{id} leads: the post's path, also when
     * the way there ends at the sign-in page, which names the post it was asked for.
     */
    private fun shared(address: HttpUrl): String? {
        if (address.pathSegments.firstOrNull() != "share") return null
        return try {
            http.newCall(request(address, appAgent)).execute().use { response ->
                val end = response.request.url
                end.queryParameter("next") ?: end.encodedPath
            }
        } catch (_: IOException) {
            null
        }
    }

    /** A post from its link preview, as the phone's browser is shown it. */
    private fun linkPreview(address: HttpUrl): SocialPost? {
        val preview = LinkPreview.parse(page(address, userAgent()) ?: return null)
        val caption = SocialCaption.extract(preview.title, preview.description)
        if (caption.isBlank() || SocialCaption.isSignInPage(preview.title, preview.description)) return null
        return SocialPost(caption, preview.image)
    }

    /** The page at [address], or null where it cannot be read or the platform sends the visitor to sign in. */
    private fun page(address: HttpUrl, agent: String): String? =
        try {
            http.newCall(request(address, agent)).execute().use { response ->
                if (!response.isSuccessful || "login" in response.request.url.encodedPath) return null
                val source = response.body.source()
                source.request(MAX_PAGE_BYTES)
                source.buffer.readUtf8(minOf(source.buffer.size, MAX_PAGE_BYTES))
            }
        } catch (_: IOException) {
            null
        }

    /** The photo at [url], or null if it cannot be loaded or is no photo. */
    suspend fun photo(url: String): Photo? =
        withContext(Dispatchers.IO) {
            val address = url.toHttpUrlOrNull() ?: return@withContext null
            try {
                http.newCall(request(address, userAgent())).execute().use { response ->
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

    private fun request(address: HttpUrl, agent: String): Request =
        Request
            .Builder()
            .url(address)
            // A device name outside ASCII would make the header invalid.
            .header("User-Agent", agent.filter { it in ' '..'~' })
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()

    companion object {
        private const val READ_SECONDS = 15L

        /** The link preview sits in a page's head; nothing after this many bytes is read. */
        private const val MAX_PAGE_BYTES = 2L * 1024 * 1024

        private const val MAX_PHOTO_BYTES = 16L * 1024 * 1024

        private const val INSTAGRAM = "instagram.com"

        private val Hosts = listOf(INSTAGRAM, "facebook.com", "fb.watch", "threads.net", "threads.com", "tiktok.com")

        /** Posts of the platforms that show recipes only in a caption or a video. */
        fun isPost(url: String): Boolean {
            val host = url.trim().toHttpUrlOrNull()?.host ?: return false
            return Hosts.any { host == it || host.endsWith(".$it") }
        }
    }
}
