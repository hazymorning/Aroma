package app.aroma.core.format

import kotlin.math.roundToInt

/**
 * A time as a recipe states it, read into whole minutes: one time, or a span from [minutes] to
 * [upTo], with what stood before it, such as "ca.", kept as [prefix].
 */
data class ReadTime(val minutes: Int, val upTo: Int? = null, val prefix: String = "")

/**
 * Reads the times recipes come with, so every page can write them one way: ISO 8601 as Mealie and
 * schema.org keep them ("PT1H20M"), and the free text of older servers and imports ("45 min",
 * "10–15 Minuten", "ca. 2 Std.", "1 hour 20 minutes", "1:20 h"). What it cannot read is null, and
 * stays as written.
 */
object TimeText {
    private const val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private const val HOURS = """(?:h|hr|hrs|std|stdn|stunde|stunden|hour|hours)\.?"""
    private const val MINUTES = """(?:m|min|mins|minute|minuten|minutes)\.?"""
    private const val DASH = """\s*(?:[-–—]|bis|to)\s*"""

    private val Prefix = Regex("""^(ca\.?|circa|etwa|ungefähr|rund|about|approx\.?|approximately|around|~)\s*""", RegexOption.IGNORE_CASE)
    private val Iso =
        Regex("""^P(?:(\d+)D)?(?:T(?:(\d+(?:[.,]\d+)?)H)?(?:(\d+(?:[.,]\d+)?)M)?(?:(\d+(?:[.,]\d+)?)S)?)?$""", RegexOption.IGNORE_CASE)
    private val HoursAndMinutes = Regex("""^$NUMBER\s*$HOURS\s*(?:und|and|,)?\s*(\d+)\s*$MINUTES$""", RegexOption.IGNORE_CASE)
    private val Clock = Regex("""^(\d+):([0-5]\d)\s*$HOURS$""", RegexOption.IGNORE_CASE)
    private val HourSpan = Regex("""^$NUMBER$DASH$NUMBER\s*$HOURS$""", RegexOption.IGNORE_CASE)
    private val MinuteSpan = Regex("""^(\d+)$DASH(\d+)\s*$MINUTES$""", RegexOption.IGNORE_CASE)
    private val Hours = Regex("""^$NUMBER\s*$HOURS$""", RegexOption.IGNORE_CASE)
    private val Minutes = Regex("""^(\d+)\s*$MINUTES$""", RegexOption.IGNORE_CASE)
    private val Bare = Regex("""^(\d+)$""")

    /** [text] read into minutes, or null where it says something else; "0 min" reads as no time at all. */
    fun read(text: String): ReadTime? {
        val trimmed = text.trim().replace(' ', ' ').replace(' ', ' ')
        if (trimmed.isEmpty()) return null
        val prefix = Prefix.find(trimmed)?.value.orEmpty()
        val rest = trimmed.removePrefix(prefix).trim()
        val time = iso(rest) ?: free(rest) ?: return null
        return time.copy(prefix = prefix.trim())
    }

    private fun iso(text: String): ReadTime? {
        val match = Iso.matchEntire(text) ?: return null
        if (text.equals("P", ignoreCase = true) || text.equals("PT", ignoreCase = true)) return null
        val (days, hours, minutes, seconds) = match.destructured
        val total =
            days.number() * MINUTES_PER_DAY + hours.number() * MINUTES_PER_HOUR + minutes.number() + seconds.number() / SECONDS_PER_MINUTE
        return ReadTime(total.roundToInt())
    }

    private fun free(text: String): ReadTime? {
        HoursAndMinutes.matchEntire(text)?.let { match ->
            val (hours, minutes) = match.destructured
            return ReadTime((hours.number() * MINUTES_PER_HOUR).roundToInt() + minutes.toInt())
        }
        Clock.matchEntire(text)?.let { match ->
            val (hours, minutes) = match.destructured
            return ReadTime(hours.toInt() * MINUTES_PER_HOUR + minutes.toInt())
        }
        HourSpan.matchEntire(text)?.let { match ->
            val (from, to) = match.destructured
            return ReadTime((from.number() * MINUTES_PER_HOUR).roundToInt(), (to.number() * MINUTES_PER_HOUR).roundToInt())
        }
        MinuteSpan.matchEntire(text)?.let { match ->
            val (from, to) = match.destructured
            return ReadTime(from.toInt(), to.toInt())
        }
        Hours.matchEntire(text)?.let { return ReadTime((it.groupValues[1].number() * MINUTES_PER_HOUR).roundToInt()) }
        Minutes.matchEntire(text)?.let { return ReadTime(it.groupValues[1].toInt()) }
        Bare.matchEntire(text)?.let { return ReadTime(it.groupValues[1].toInt()) }
        return null
    }

    private fun String.number(): Double = if (isEmpty()) 0.0 else replace(',', '.').toDouble()

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val SECONDS_PER_MINUTE = 60.0
}
