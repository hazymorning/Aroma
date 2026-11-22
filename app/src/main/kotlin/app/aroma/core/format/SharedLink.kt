package app.aroma.core.format

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Links shared from apps carry parameters that identify who shared them with whom. They mean
 * nothing to the recipe, and Mealie would keep them for good as the recipe's source.
 */
object SharedLink {
    private val Tracking = setOf("igsh", "igshid", "fbclid", "gclid", "si", "mibextid", "share_id", "mc_cid", "mc_eid", "ref_src")

    fun clean(link: String): String {
        val url = link.trim().toHttpUrlOrNull() ?: return link.trim()
        val tracking = url.queryParameterNames.filter { it.lowercase() in Tracking || it.lowercase().startsWith("utm_") }
        if (tracking.isEmpty()) return link.trim()
        return url
            .newBuilder()
            .apply { tracking.forEach { removeAllQueryParameters(it) } }
            .build()
            .toString()
            .removeSuffix("?")
    }
}
