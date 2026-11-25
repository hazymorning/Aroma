package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationDetectorTest {
    /** The words each timer of [text] is set on, with its seconds. */
    private fun timers(text: String) = DurationDetector.detect(text).map { text.substring(it.start, it.end) to it.seconds }

    @Test
    fun `a time in a step becomes a timer on its words`() =
        listOf(
            "Den Lauch etwa 10 Minuten anbraten." to listOf("10 Minuten" to 600),
            "Im Ofen 1,5 Stunden schmoren." to listOf("1,5 Stunden" to 5400),
            "Mindestens 2 h kalt stellen." to listOf("2 h" to 7200),
            "Blend for 45 seconds." to listOf("45 seconds" to 45),
            "Let it rest for a minute." to listOf("a minute" to 60),
            "Eine Stunde gehen lassen." to listOf("Eine Stunde" to 3600),
            "Eine halbe Stunde ziehen lassen." to listOf("halbe Stunde" to 1800),
            "Rest for half an hour." to listOf("half an hour" to 1800),
            "½ Stunde quellen lassen." to listOf("½ Stunde" to 1800),
            // Hours and minutes together are one timer; times apart are two.
            "Insgesamt 1 Std. 30 Min. garen." to listOf("1 Std. 30 Min." to 5400),
            "Cook for 1 hour and 15 minutes." to listOf("1 hour and 15 minutes" to 4500),
            "5 Minuten anbraten, dann 20 Minuten köcheln." to listOf("5 Minuten" to 300, "20 Minuten" to 1200),
            // Temperatures and amounts are no times.
            "Bei 180 Grad backen, 200 g Mehl dazugeben." to emptyList(),
            "Add 2 minced garlic cloves." to emptyList(),
        ).forEach { (text, timers) -> assertEquals(text, timers, timers(text)) }

    @Test
    fun `a range starts at its lower end`() {
        val range = DurationDetector.detect("25-30 Minuten backen").single()
        assertEquals(DetectedDuration(0, 13, 1500, 1800), range)
    }
}
