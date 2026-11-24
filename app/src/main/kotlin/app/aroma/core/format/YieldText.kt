package app.aroma.core.format

/**
 * A recipe's yield as Mealie keeps it in its text: a count, perhaps a range, and what it counts,
 * with what stands before the count ("ca. 24 Stück", "1 Blech", "4–6 Portionen"). Where Mealie keeps
 * the count apart, the text is what it counts alone ("Stück") and [quantity] is null.
 */
data class YieldText(
    val prefix: String,
    val quantity: Double?,
    val upTo: Double?,
    val unit: String,
) {
    companion object {
        private val Counted = Regex("""^(.*?)(\d+(?:[.,]\d+)?)(?:\s*[–-]\s*(\d+(?:[.,]\d+)?))?\s*(.*)$""")

        /** The yield in [text], or null where there is none. */
        fun parse(text: String?): YieldText? {
            val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val match = Counted.matchEntire(trimmed) ?: return YieldText("", null, null, trimmed)
            val (prefix, from, to, unit) = match.destructured
            return YieldText(
                prefix = prefix.trim(),
                quantity = from.number(),
                upTo = to.takeIf { it.isNotEmpty() }?.number(),
                unit = unit.trim(),
            )
        }

        private fun String.number(): Double? = replace(',', '.').toDoubleOrNull()
    }
}
