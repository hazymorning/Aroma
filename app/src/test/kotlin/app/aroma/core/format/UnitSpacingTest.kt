package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitSpacingTest {
    private val n = " "

    @Test
    fun `numbers stay with their units`() {
        assertEquals("500${n}g Mehl und 30${n}Min. ruhen lassen", UnitSpacing.apply("500 g Mehl und 30 Min. ruhen lassen"))
        assertEquals("bei 180$n°C 7${n}Minuten backen", UnitSpacing.apply("bei 180 °C 7 Minuten backen"))
        assertEquals("bake for 10${n}minutes", UnitSpacing.apply("bake for 10 minutes"))
    }

    @Test
    fun `words that only start like a unit stay apart`() {
        assertEquals("2 lange Stangen und 3 große Eier", UnitSpacing.apply("2 lange Stangen und 3 große Eier"))
        assertEquals("in 2 gleich große Stücke", UnitSpacing.apply("in 2 gleich große Stücke"))
    }

    @Test
    fun `the length stays the same`() {
        val text = "1 l Brühe, 2 EL Öl"
        assertEquals(text.length, UnitSpacing.apply(text).length)
    }
}
