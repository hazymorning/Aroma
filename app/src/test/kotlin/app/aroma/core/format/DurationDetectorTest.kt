package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationDetectorTest {
    private fun seconds(text: String) = DurationDetector.detect(text).map { it.seconds }

    @Test
    fun `german minutes and hours`() {
        assertEquals(listOf(600), seconds("Den Lauch etwa 10 Minuten anbraten."))
        assertEquals(listOf(3600), seconds("Den Teig 1 Stunde ruhen lassen."))
        assertEquals(listOf(5400), seconds("Im Ofen 1,5 Stunden schmoren."))
        assertEquals(listOf(7200), seconds("Mindestens 2 h kalt stellen."))
    }

    @Test
    fun `english units and abbreviations`() {
        assertEquals(listOf(1800), seconds("Bake for 30 minutes until golden."))
        assertEquals(listOf(300), seconds("Simmer 5 mins."))
        assertEquals(listOf(45), seconds("Blend for 45 seconds."))
        assertEquals(listOf(60), seconds("Let it rest for a minute."))
    }

    @Test
    fun `combined hours and minutes become one timer`() {
        assertEquals(listOf(5400), seconds("Insgesamt 1 Std. 30 Min. garen."))
        assertEquals(listOf(4500), seconds("Cook for 1 hour and 15 minutes."))
    }

    @Test
    fun `ranges start at the lower bound`() {
        val found = DurationDetector.detect("25-30 Minuten backen")
        assertEquals(1, found.size)
        assertEquals(1500, found[0].seconds)
        assertEquals(1800, found[0].upperSeconds)
        assertEquals("25-30 Minuten", "25-30 Minuten backen".substring(found[0].start, found[0].end))
    }

    @Test
    fun `number words and halves`() {
        assertEquals(listOf(3600), seconds("Eine Stunde gehen lassen."))
        assertEquals(listOf(1800), seconds("Eine halbe Stunde ziehen lassen."))
        assertEquals(listOf(1800), seconds("Rest for half an hour."))
        assertEquals(listOf(1800), seconds("½ Stunde quellen lassen."))
    }

    @Test
    fun `separate durations stay separate`() {
        assertEquals(listOf(300, 1200), seconds("5 Minuten anbraten, dann 20 Minuten köcheln."))
    }

    @Test
    fun `temperatures and amounts are not durations`() {
        assertTrue(seconds("Bei 180 Grad backen, 200 g Mehl dazugeben.").isEmpty())
        assertTrue(seconds("Add 2 minced garlic cloves.").isEmpty())
        assertTrue(seconds("Die Hähnchenbrust in Streifen schneiden.").isEmpty())
    }
}
