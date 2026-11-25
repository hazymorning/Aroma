package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadingQuantityParserTest {
    @Test
    fun `an amount at the start is read in every spelling, a range with both ends`() =
        listOf(
            "2 Stangen Lauch (groß)" to LeadingQuantity(2.0, null, "Stangen Lauch (groß)"),
            "200g Mehl" to LeadingQuantity(200.0, null, "g Mehl"),
            "1,5 kg Kartoffeln" to LeadingQuantity(1.5, null, "kg Kartoffeln"),
            "1.5 cups milk" to LeadingQuantity(1.5, null, "cups milk"),
            "1,05 kg Kartoffeln" to LeadingQuantity(1.05, null, "kg Kartoffeln"),
            "0.05 l Rum" to LeadingQuantity(0.05, null, "l Rum"),
            "1/2 TL Salz" to LeadingQuantity(0.5, null, "TL Salz"),
            "½ Zitrone" to LeadingQuantity(0.5, null, "Zitrone"),
            "1 1/2 Tassen Mehl" to LeadingQuantity(1.5, null, "Tassen Mehl"),
            "1½ Tassen Mehl" to LeadingQuantity(1.5, null, "Tassen Mehl"),
            "1 ½ Tassen Mehl" to LeadingQuantity(1.5, null, "Tassen Mehl"),
            "3-4 Kartoffeln" to LeadingQuantity(3.0, 4.0, "Kartoffeln"),
            "2 – 3 EL Öl" to LeadingQuantity(2.0, 3.0, "EL Öl"),
            "2 bis 3 EL Öl" to LeadingQuantity(2.0, 3.0, "EL Öl"),
            "2 to 3 tbsp oil" to LeadingQuantity(2.0, 3.0, "tbsp oil"),
            // "bis" and "to" join two numbers only.
            "4 Tomaten" to LeadingQuantity(4.0, null, "Tomaten"),
            "1 Biskuitboden" to LeadingQuantity(1.0, null, "Biskuitboden"),
            // A second number is no fraction of the first.
            "1 12-oz can tomatoes" to LeadingQuantity(1.0, null, "12-oz can tomatoes"),
        ).forEach { (text, quantity) -> assertEquals(text, quantity, LeadingQuantityParser.parse(text)) }

    @Test
    fun `text without an amount to scale is left alone`() =
        listOf("Salz und Pfeffer", "Saft von 1 Zitrone", "Olivenöl (zum Ausbacken)", "4", "1. Schritt").forEach {
            assertNull(it, LeadingQuantityParser.parse(it))
        }
}
