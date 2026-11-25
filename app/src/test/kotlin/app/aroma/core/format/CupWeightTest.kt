package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CupWeightTest {
    @Test
    fun `a food is found once words that don't change its weight are left out`() =
        listOf(
            "unbleached all-purpose flour" to CupWeight.Flour,
            "Weizenmehl Type 405" to CupWeight.Flour,
            "confectioners' sugar" to CupWeight.PowderedSugar,
            "shredded sharp cheddar" to CupWeight.GratedCheese,
            "geriebener Käse" to CupWeight.GratedCheese,
            "Walnüsse, gehackt" to CupWeight.ChoppedNuts,
        ).forEach { (food, weight) -> assertEquals(food, weight, CupWeight.of(food)) }

    @Test
    fun `brackets and prep add nothing, while the note tells how the food is cut`() {
        assertEquals(CupWeight.Flour, CupWeight.of("Mehl (Type 550)"))
        assertEquals(CupWeight.Flour, CupWeight.of("all-purpose flour, plus more for dusting"))
        assertEquals(CupWeight.Butter, CupWeight.of("(2 sticks) unsalted butter, cut into cubes"))
        assertEquals(CupWeight.SlicedAlmonds, CupWeight.of("almonds", "sliced"))
        assertEquals(CupWeight.ChoppedNuts, CupWeight.of("walnuts", "coarsely chopped"))
        assertEquals(CupWeight.Almonds, CupWeight.of("almonds", "toasted"))
    }

    @Test
    fun `foods whose weight per cup is uncertain or unknown match nothing`() =
        listOf(
            "kosher salt",
            "walnuts",
            "almonds, chopped",
            "almond flour",
            "gemahlene Mandeln",
            "sifted powdered sugar",
            "cooked rice",
            "Greek yogurt",
            "buttermilk",
            "Milch",
            "softened",
            "",
        ).forEach { assertNull(it, CupWeight.of(it)) }
}
