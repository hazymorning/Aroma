package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `free text reads in German and English`() {
        assertEquals(ReadTime(45), TimeText.read("45 min"))
        assertEquals(ReadTime(45), TimeText.read("45 Minuten"))
        assertEquals(ReadTime(80), TimeText.read("1 Std. 20 Min."))
        assertEquals(ReadTime(80), TimeText.read("1 hour 20 minutes"))
        assertEquals(ReadTime(80), TimeText.read("1:20 h"))
        assertEquals(ReadTime(90), TimeText.read("1,5 Stunden"))
        assertEquals(ReadTime(30), TimeText.read("30"))
    }

    @Test
    fun `a span keeps both ends`() {
        assertEquals(ReadTime(10, 15), TimeText.read("10–15 Minuten"))
        assertEquals(ReadTime(10, 15), TimeText.read("10-15 min"))
        assertEquals(ReadTime(10, 15), TimeText.read("10 bis 15 Minuten"))
        assertEquals(ReadTime(60, 120), TimeText.read("1–2 Std."))
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
