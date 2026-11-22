package app.kochi.core.format

/** A duration mentioned in a cooking step, e.g. "10 Minuten" or "1 hour and 15 minutes". */
data class DetectedDuration(
    val start: Int,
    val end: Int,
    val seconds: Int,
    val upperSeconds: Int?,
)

/** Finds durations in German and English instruction text. */
object DurationDetector {
    private const val NUMBER =
        """\d+(?:[.,]\d+)?(?:/\d+)?|[½¼¾⅓⅔]|ein(?:e|er|en)?|one|a|an|zwei|two|drei|three|vier|four|fünf|five|sechs|six|zehn|ten|zwanzig|twenty|dreißig|thirty"""
    private const val UNIT =
        """stunden|stunde|std\.?|hours|hour|hrs\.?|hr\.?|h|minuten|minute|min\.?|mins\.?|minutes|sekunden|sekunde|sek\.?|seconds|second|secs\.?|sec\.?"""

    private val single =
        Regex(
            """(?<![\p{L}\d])(?:($NUMBER)\s*(?:(?:-|–|bis|to)\s*($NUMBER)\s*)?($UNIT)(?![\p{L}])|(halbe[nr]?\s+stunde|half\s+an\s+hour))""",
            RegexOption.IGNORE_CASE,
        )
    private val joiner = Regex("""^\s*(?:,|und|and)?\s*$""", RegexOption.IGNORE_CASE)

    private val words =
        mapOf(
            "ein" to 1.0,
            "eine" to 1.0,
            "einer" to 1.0,
            "einen" to 1.0,
            "one" to 1.0,
            "a" to 1.0,
            "an" to 1.0,
            "zwei" to 2.0,
            "two" to 2.0,
            "drei" to 3.0,
            "three" to 3.0,
            "vier" to 4.0,
            "four" to 4.0,
            "fünf" to 5.0,
            "five" to 5.0,
            "sechs" to 6.0,
            "six" to 6.0,
            "zehn" to 10.0,
            "ten" to 10.0,
            "zwanzig" to 20.0,
            "twenty" to 20.0,
            "dreißig" to 30.0,
            "thirty" to 30.0,
        )

    fun detect(text: String): List<DetectedDuration> {
        val parts = single.findAll(text).mapNotNull { match -> toDuration(match) }.toList()
        if (parts.isEmpty()) return emptyList()
        val merged = mutableListOf<DetectedDuration>()
        for (part in parts) {
            val previous = merged.lastOrNull()
            val canMerge =
                previous != null &&
                    previous.upperSeconds == null &&
                    part.upperSeconds == null &&
                    previous.seconds > part.seconds &&
                    joiner.matches(text.substring(previous.end, part.start))
            if (canMerge) {
                merged[merged.lastIndex] = previous.copy(end = part.end, seconds = previous.seconds + part.seconds)
            } else {
                merged += part
            }
        }
        return merged.filter { it.seconds in 1..(48 * 3600) }
    }

    private fun toDuration(match: MatchResult): DetectedDuration? {
        val range = match.range
        if (match.groupValues[4].isNotEmpty()) return DetectedDuration(range.first, range.last + 1, 1800, null)
        val amount = number(match.groupValues[1]) ?: return null
        val upper = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::number)
        val factor = unitSeconds(match.groupValues[3]) ?: return null
        val seconds = (amount * factor).toInt()
        val upperSeconds = upper?.let { (it * factor).toInt() }?.takeIf { it > seconds }
        return DetectedDuration(range.first, range.last + 1, seconds, upperSeconds)
    }

    private fun number(raw: String): Double? {
        if (raw.length == 1) GLYPH_VALUES[raw[0]]?.let { return it }
        if ('/' in raw) {
            val (numerator, denominator) = raw.split('/').map { it.toDoubleOrNull() ?: return null }
            return if (denominator == 0.0) null else numerator / denominator
        }
        return raw.replace(',', '.').toDoubleOrNull() ?: words[raw.lowercase()]
    }

    private fun unitSeconds(raw: String): Int? {
        val unit = raw.lowercase().trimEnd('.')
        return when {
            unit.startsWith("st") || unit.startsWith("h") -> 3600
            unit.startsWith("min") -> 60
            unit.startsWith("se") -> 1
            else -> null
        }
    }
}
