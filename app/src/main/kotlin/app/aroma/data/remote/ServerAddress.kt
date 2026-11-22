package app.aroma.data.remote

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
        if (hasScheme) return listOfNotNull("$withoutUi/".toHttpUrlOrNull())
        val secure = "https://$withoutUi/".toHttpUrlOrNull() ?: return emptyList()
        // Plain http only at home. Elsewhere a network that blocks https could otherwise make
        // Aroma send the password unencrypted; typing http:// still works deliberately.
        return if (isPrivate(secure.host)) listOfNotNull(secure, "http://$withoutUi/".toHttpUrlOrNull()) else listOf(secure)
    }

    /** Hosts only reachable at home or through a private tunnel, where plain http is common. */
    fun isPrivate(host: String): Boolean {
        val name = host.lowercase().removeSuffix(".")
        if (name == "localhost" || ('.' !in name && ':' !in name)) return true
        if (PrivateSuffixes.any { name.endsWith(it) }) return true
        val v4 = name.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && name.count { c -> c == '.' } == 3 }
        if (v4 != null) {
            val (a, b) = v4
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        return ':' in name && (name == "::1" || name.startsWith("fc") || name.startsWith("fd") || name.startsWith("fe80"))
    }

    private val PrivateSuffixes = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain", ".fritz.box", ".ts.net")

    /** Base URL as stored in the session: no trailing slash. */
    fun display(url: HttpUrl): String = url.toString().trimEnd('/')
}
