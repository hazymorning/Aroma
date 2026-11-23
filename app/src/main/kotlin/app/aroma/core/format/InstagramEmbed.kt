package app.aroma.core.format

/**
 * An Instagram post as its embed shows it to other websites (".../p/{code}/embed/captioned/"):
 * the whole caption and the photo sit in the page itself, for visitors who are not signed in.
 * A browser gets a page that loads them by script instead, so the request must not pose as one.
 */
object InstagramEmbed {
    private val Code = Regex("""/(?:p|reels?|tv)/([A-Za-z0-9_-]+)""")
    private const val CAPTION = """<div class="Caption">"""
    private const val COMMENTS = """<div class="CaptionComments">"""
    private val Author = Regex("""<a class="CaptionUsername"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL)
    private val LineBreak = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val Tag = Regex("""<[^>]+>""")
    private val Blank = Regex("""\n{3,}""")
    private val Photo = Regex("""<img\b[^>]*\bclass="EmbeddedMediaImage"[^>]*>""")
    private val Source = Regex("""\bsrc="([^"]+)"""")

    /**
     * The post's code in a path such as "/reel/{code}/" or "/{user}/p/{code}/", or null. A share
     * link ("/share/reel/{id}/") names no post until it is followed.
     */
    fun code(path: String): String? = if (path.startsWith("/share/")) null else Code.find(path)?.groupValues?.get(1)

    /** The caption with its line breaks, without its author's name, or null where the page shows none. */
    fun caption(html: String): String? {
        val start = html.indexOf(CAPTION).takeIf { it >= 0 } ?: return null
        val body = html.substring(start + CAPTION.length)
        // The caption ends where its comments link or its own block does, whichever comes first.
        val end = listOf(body.indexOf(COMMENTS), body.indexOf("</div>")).filter { it >= 0 }.minOrNull() ?: body.length
        val text =
            LinkPreview.decode(
                body
                    .substring(0, end)
                    .replace(Author, "")
                    .replace(LineBreak, "\n")
                    .replace(Tag, ""),
            )
        return text
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Blank, "\n\n")
            .trim()
            .ifEmpty { null }
    }

    /** The address of the post's photo, or null. */
    fun photo(html: String): String? =
        Photo
            .find(html)
            ?.let { Source.find(it.value) }
            ?.groupValues
            ?.get(1)
            ?.let(LinkPreview::decode)
}
