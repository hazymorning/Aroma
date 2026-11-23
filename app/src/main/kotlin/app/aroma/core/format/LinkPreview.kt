package app.aroma.core.format

/**
 * What a page tells link previews about itself, the way messengers show a shared link: its
 * `og:` meta tags, or the `twitter:` ones where those are missing.
 */
data class LinkPreview(
    val title: String,
    val description: String,
    val image: String?,
    val isVideo: Boolean,
) {
    companion object {
        // A quoted value may hold a ">", so the tag ends at the first one outside quotes.
        private val Meta = Regex("""<meta\b(?:[^>"']|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
        private val Attribute = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        private val Entity = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")
        private val Named = mapOf("amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to "\u00a0")

        fun parse(html: String): LinkPreview {
            val tags =
                Meta
                    .findAll(html)
                    .mapNotNull { tag ->
                        val attributes =
                            Attribute.findAll(tag.value).associate { match ->
                                match.groupValues[1].lowercase() to match.groupValues[2].ifEmpty { match.groupValues[3] }
                            }
                        val key = attributes["property"] ?: attributes["name"] ?: return@mapNotNull null
                        val content = attributes["content"] ?: return@mapNotNull null
                        key.lowercase() to decode(content)
                    }.toList()

            fun first(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { key -> tags.firstOrNull { it.first == key && it.second.isNotBlank() }?.second }

            return LinkPreview(
                title = first("og:title", "twitter:title").orEmpty(),
                description = first("og:description", "twitter:description", "description").orEmpty(),
                image = first("og:image:secure_url", "og:image", "twitter:image"),
                isVideo = first("og:video", "og:video:secure_url") != null || first("og:type")?.startsWith("video") == true,
            )
        }

        /** Resolves character references, keeping line breaks: a caption's lines carry its structure. */
        private fun decode(text: String): String =
            Entity.replace(text) { match ->
                val name = match.groupValues[1]
                val codePoint =
                    when {
                        name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
                        name.startsWith("#") -> name.drop(1).toIntOrNull()
                        else -> null
                    }
                when {
                    codePoint != null && Character.isValidCodePoint(codePoint) -> String(Character.toChars(codePoint))
                    else -> Named[name] ?: match.value
                }
            }
    }
}
