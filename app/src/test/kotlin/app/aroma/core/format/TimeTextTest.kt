package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTextTest {
    @Test
    fun `ISO 8601 durations read as minutes`() {
        assertEquals(ReadTime(80), TimeText.read("PT1H20M"))
        assertEquals(ReadTime(45), TimeText.read("PT45M"))
        assertEquals(ReadTime(120), TimeText.read("P0DT2H"))
        assertEquals(ReadTime(2), TimeText.read("PT90S"))
    }

    @Test
    fun `free text reads in German and English, on a recipe's page as in the timers of its steps`() {
        listOf(
            "45 min" to ReadTime(45),
            "45 Minuten" to ReadTime(45),
            "1 Std. 20 Min." to ReadTime(80),
            "1 hour 20 minutes" to ReadTime(80),
            "1:20 h" to ReadTime(80),
            "1,5 Stunden" to ReadTime(90),
            "10–15 Minuten" to ReadTime(10, 15),
            "10-15 min" to ReadTime(10, 15),
            "10 bis 15 Minuten" to ReadTime(10, 15),
            "1–2 Std." to ReadTime(60, 120),
        ).forEach { (text, time) ->
            assertEquals(text, time, TimeText.read(text))
            val timer = DurationDetector.detect(text).single()
            assertEquals(text, time, ReadTime(timer.seconds / 60, timer.upperSeconds?.div(60)))
            assertEquals(text, text, text.substring(timer.start, timer.end))
        }
        // Mealie may keep a time as a bare number of minutes; in a step, a number alone is no timer.
        assertEquals(ReadTime(30), TimeText.read("30"))
        assertTrue(DurationDetector.detect("30").isEmpty())
    }

    @Test
    fun `what stands before the time stays`() {
        assertEquals(ReadTime(120, prefix = "ca."), TimeText.read("ca. 2 Std."))
        assertEquals(ReadTime(30, prefix = "about"), TimeText.read("about 30 minutes"))
    }

    @Test
    fun `no time at all reads as zero, and anything else not at all`() {
        assertEquals(ReadTime(0), TimeText.read("PT0S"))
        assertEquals(ReadTime(0), TimeText.read("0 min"))
        assertNull(TimeText.read(""))
        assertNull(TimeText.read("Über Nacht"))
        assertNull(TimeText.read("PT"))
    }
}
