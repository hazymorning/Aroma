package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadingQuantityParserTest {
    private fun parse(text: String) = LeadingQuantityParser.parse(text)

    @Test
    fun `integer with unit`() {
        assertEquals(LeadingQuantity(2.0, null, "Stangen Lauch (groß)"), parse("2 Stangen Lauch (groß)"))
    }

    @Test
    fun `number glued to the unit`() {
        assertEquals(LeadingQuantity(200.0, null, "g Mehl"), parse("200g Mehl"))
    }

    @Test
    fun `decimal comma and point`() {
        assertEquals(1.5, parse("1,5 kg Kartoffeln")!!.value, 0.0)
        assertEquals(1.5, parse("1.5 cups milk")!!.value, 0.0)
    }

    @Test
    fun `decimals keep their leading zeros`() {
        assertEquals(1.05, parse("1,05 kg Kartoffeln")!!.value, 0.0)
        assertEquals(0.05, parse("0.05 l Rum")!!.value, 0.0)
    }

    @Test
    fun `fractions in all common spellings`() {
        assertEquals(0.5, parse("1/2 TL Salz")!!.value, 0.0)
        assertEquals(0.5, parse("½ Zitrone")!!.value, 0.0)
        assertEquals(1.5, parse("1 1/2 Tassen Mehl")!!.value, 0.0)
        assertEquals(1.5, parse("1½ Tassen Mehl")!!.value, 0.0)
        assertEquals(1.5, parse("1 ½ Tassen Mehl")!!.value, 0.0)
    }

    @Test
    fun `ranges keep both bounds`() {
        assertEquals(LeadingQuantity(3.0, 4.0, "Kartoffeln"), parse("3-4 Kartoffeln"))
        assertEquals(LeadingQuantity(2.0, 3.0, "EL Öl"), parse("2 – 3 EL Öl"))
    }

    @Test
    fun `a second number is not mistaken for a fraction`() {
        assertEquals(LeadingQuantity(1.0, null, "12-oz can tomatoes"), parse("1 12-oz can tomatoes"))
    }

    @Test
    fun `text without a leading amount is left alone`() {
        assertNull(parse("Salz und Pfeffer"))
        assertNull(parse("Saft von 1 Zitrone"))
        assertNull(parse("Olivenöl (zum Ausbacken)"))
    }

    @Test
    fun `a bare number or a numbered heading is not an ingredient amount`() {
        assertNull(parse("4"))
        assertNull(parse("1. Schritt"))
    }
}
