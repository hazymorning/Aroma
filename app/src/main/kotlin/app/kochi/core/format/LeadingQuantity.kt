package app.kochi.core.format

/**
 * Quantity found at the start of free text such as "2 1/2 EL Olivenöl" or "3-4 Kartoffeln".
 * Mealie keeps unparsed ingredients as plain notes; this makes them scalable anyway.
 */
data class LeadingQuantity(val value: Double, val upper: Double?, val rest: String)

object LeadingQuantityParser {
    fun parse(text: String): LeadingQuantity? {
        val reader = Reader(text)
        reader.skipSpaces()
        val value = reader.readAmount() ?: return null
        val afterValue = reader.position
        reader.skipSpaces()
        var upper: Double? = null
        if (reader.peek() == '-' || reader.peek() == '–') {
            reader.advance()
            reader.skipSpaces()
            upper = reader.readAmount()
            if (upper == null || upper <= value) {
                upper = null
                reader.position = afterValue
            }
        } else {
            reader.position = afterValue
        }
        val rest = text.substring(reader.position).trim()
        if (rest.isEmpty() || rest.first() == '.' || rest.first() == ')') return null
        return LeadingQuantity(value, upper, rest)
    }

    private class Reader(val text: String) {
        var position = 0

        fun peek(): Char? = text.getOrNull(position)

        fun advance() {
            position++
        }

        fun skipSpaces() {
            while (peek()?.isWhitespace() == true) position++
        }

        /** Reads "1", "1.5", "1,5", "1/2", "½", "1 1/2" or "1½". */
        fun readAmount(): Double? {
            val start = position
            peek()?.let { glyph ->
                GLYPH_VALUES[glyph]?.let {
                    advance()
                    return it
                }
            }
            val whole = readDigits() ?: return null
            if (peek() == '/') {
                val slash = position
                advance()
                val denominator = readDigits()
                if (denominator != null && denominator.toInt() != 0) return whole.toDouble() / denominator.toDouble()
                position = slash
                return whole.toDouble()
            }
            if ((peek() == '.' || peek() == ',') && text.getOrNull(position + 1)?.isDigit() == true) {
                advance()
                val decimals = readDigits() ?: return null
                return "$whole.$decimals".toDouble()
            }
            val afterWhole = position
            peek()?.let { glyph ->
                GLYPH_VALUES[glyph]?.let {
                    advance()
                    return whole.toDouble() + it
                }
            }
            skipSpaces()
            GLYPH_VALUES[peek() ?: ' ']?.let {
                advance()
                return whole.toDouble() + it
            }
            val numerator = readDigits()
            if (numerator != null && peek() == '/') {
                advance()
                val denominator = readDigits()
                if (denominator != null && denominator.toInt() != 0 && numerator.toInt() < denominator.toInt()) {
                    return whole.toDouble() + numerator.toDouble() / denominator.toDouble()
                }
            }
            position = afterWhole
            return if (position > start) whole.toDouble() else null
        }

        private fun readDigits(): Long? {
            val start = position
            while (peek()?.isDigit() == true) position++
            if (position == start || position - start > 9) {
                position = start
                return null
            }
            return text.substring(start, position).toLong()
        }
    }
}
