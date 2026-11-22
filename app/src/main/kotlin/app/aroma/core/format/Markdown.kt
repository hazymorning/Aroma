package app.aroma.core.format

/** Plain text plus the ranges that carry formatting. */
data class StyledText(val text: String, val spans: List<Span>) {
    sealed interface Span {
        val start: Int
        val end: Int

        data class Bold(override val start: Int, override val end: Int) : Span

        data class Italic(override val start: Int, override val end: Int) : Span

        data class Link(override val start: Int, override val end: Int, val url: String) : Span
    }
}

/**
 * The subset of Markdown people actually use in Mealie recipes: emphasis, links,
 * headings and bullet lists. Anything else is shown as written.
 */
object Markdown {
    private val heading = Regex("""^\s{0,3}#{1,6}\s+""")
    private val bullet = Regex("""^(\s*)[-*+]\s+""")
    private val bareUrl = Regex("""https?://[^\s)\]]+""")

    fun parse(markdown: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<StyledText.Span>()
        markdown.replace("\r\n", "\n").split('\n').forEachIndexed { index, rawLine ->
            if (index > 0) out.append('\n')
            var line = rawLine
            val isHeading = heading.containsMatchIn(line)
            if (isHeading) line = line.replace(heading, "")
            bullet.find(line)?.let { match -> line = match.groupValues[1] + "• " + line.substring(match.range.last + 1) }
            val lineStart = out.length
            parseInline(line, out, spans)
            if (isHeading && out.length > lineStart) spans += StyledText.Span.Bold(lineStart, out.length)
        }
        return StyledText(out.toString(), spans.sortedBy { it.start })
    }

    private fun parseInline(line: String, out: StringBuilder, spans: MutableList<StyledText.Span>) {
        var i = 0
        while (i < line.length) {
            val rest = line.substring(i)
            when {
                rest.startsWith("**") || rest.startsWith("__") -> {
                    val marker = rest.substring(0, 2)
                    val close = line.indexOf(marker, i + 2)
                    if (close > i + 2) {
                        val start = out.length
                        out.append(line, i + 2, close)
                        spans += StyledText.Span.Bold(start, out.length)
                        i = close + 2
                        continue
                    }
                }

                (rest.startsWith("*") || rest.startsWith("_")) && isEmphasisStart(line, i) -> {
                    val marker = line[i]
                    val close = findEmphasisEnd(line, i + 1, marker)
                    if (close > i + 1) {
                        val start = out.length
                        out.append(line, i + 1, close)
                        spans += StyledText.Span.Italic(start, out.length)
                        i = close + 1
                        continue
                    }
                }

                rest.startsWith("[") -> {
                    val closeText = line.indexOf("](", i)
                    val closeUrl = if (closeText > 0) line.indexOf(')', closeText) else -1
                    if (closeText > i && closeUrl > closeText) {
                        val start = out.length
                        out.append(line, i + 1, closeText)
                        spans += StyledText.Span.Link(start, out.length, line.substring(closeText + 2, closeUrl).trim())
                        i = closeUrl + 1
                        continue
                    }
                }

                rest.startsWith("http://") || rest.startsWith("https://") -> {
                    val url = bareUrl.find(rest)?.value
                    if (url != null) {
                        val start = out.length
                        out.append(url)
                        spans += StyledText.Span.Link(start, out.length, url)
                        i += url.length
                        continue
                    }
                }
            }
            out.append(line[i])
            i++
        }
    }

    private fun isEmphasisStart(line: String, index: Int): Boolean {
        val before = line.getOrNull(index - 1)
        val after = line.getOrNull(index + 1)
        return (before == null || !before.isLetterOrDigit()) && after != null && !after.isWhitespace()
    }

    private fun findEmphasisEnd(line: String, from: Int, marker: Char): Int {
        var j = line.indexOf(marker, from)
        while (j > 0) {
            val after = line.getOrNull(j + 1)
            if (!line[j - 1].isWhitespace() && (after == null || !after.isLetterOrDigit())) return j
            j = line.indexOf(marker, j + 1)
        }
        return -1
    }
}
