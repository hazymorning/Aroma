package app.kochi.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns what people type ("mealie.home", "192.168.1.5:9000/") into base URLs worth trying. */
object ServerAddress {
    fun candidates(input: String): List<HttpUrl> {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return emptyList()
        val withoutUi =
            trimmed
                .removeSuffix("/g/home")
                .removeSuffix("/login")
                .removeSuffix("/api")
                .trimEnd('/')
        val hasScheme = withoutUi.startsWith("http://", ignoreCase = true) || withoutUi.startsWith("https://", ignoreCase = true)
        val raw = if (hasScheme) listOf(withoutUi) else listOf("https://$withoutUi", "http://$withoutUi")
        return raw.mapNotNull { "$it/".toHttpUrlOrNull() }
    }

    /** Base URL as stored in the session: no trailing slash. */
    fun display(url: HttpUrl): String = url.toString().trimEnd('/')
}
