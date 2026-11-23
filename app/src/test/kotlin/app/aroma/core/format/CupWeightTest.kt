package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CupWeightTest {
    @Test
    fun `English and German names find the same food`() =
        listOf(
            CupWeight.Flour to
                listOf("flour", "all-purpose flour", "All Purpose Flour", "unbleached all-purpose flour", "Mehl", "Weizenmehl Type 405"),
            CupWeight.Sugar to listOf("sugar", "granulated sugar", "white sugar", "Zucker", "weißer Zucker"),
            CupWeight.BrownSugar to listOf("brown sugar", "firmly packed dark brown sugar", "brown sugar, packed", "brauner Zucker"),
            CupWeight.PowderedSugar to
                listOf("powdered sugar", "confectioners' sugar", "confectioner's sugar", "icing sugar", "Puderzucker"),
            CupWeight.Butter to listOf("butter", "unsalted butter, cold", "Butter", "weiche Butter"),
            CupWeight.Oil to listOf("vegetable oil", "neutral oil", "canola oil", "Öl", "neutrales Öl", "Rapsöl"),
            CupWeight.OliveOil to listOf("olive oil", "extra virgin olive oil", "Olivenöl", "natives Olivenöl extra"),
            CupWeight.Honey to listOf("honey", "runny honey", "Honig", "flüssiger Honig"),
            CupWeight.MapleSyrup to listOf("pure maple syrup", "Ahornsirup"),
            CupWeight.Rice to listOf("uncooked long grain rice", "basmati rice", "Reis", "Basmatireis"),
            CupWeight.Oats to listOf("quick-cooking oats", "old fashioned rolled oats", "Haferflocken", "kernige Haferflocken"),
            CupWeight.Cocoa to listOf("Dutch-process cocoa powder", "unsweetened cocoa", "Kakaopulver", "Backkakao"),
            CupWeight.Salt to listOf("salt", "table salt", "Salz", "feines Salz"),
            CupWeight.BakingPowder to listOf("baking powder", "Backpulver"),
            CupWeight.BakingSoda to listOf("baking soda", "bicarbonate of soda", "Natron"),
            CupWeight.ChoppedNuts to listOf("chopped walnuts", "walnuts, chopped", "chopped pecans", "gehackte Nüsse", "Walnüsse, gehackt"),
            CupWeight.Almonds to listOf("almonds", "whole raw almonds", "Mandeln", "ganze Mandeln"),
            CupWeight.SlicedAlmonds to listOf("sliced almonds", "almonds, sliced", "Mandelblättchen", "gehobelte Mandeln"),
            CupWeight.GratedCheese to
                listOf("shredded cheese", "cheddar cheese, shredded", "shredded sharp cheddar", "geriebener Käse", "Käse, gerieben"),
            CupWeight.Parmesan to listOf("grated Parmesan", "Parmigiano-Reggiano", "geriebener Parmesan"),
            CupWeight.ChocolateChips to listOf("semisweet chocolate chips", "mini chocolate chips", "Schokotropfen"),
            CupWeight.Cornstarch to listOf("cornstarch", "Speisestärke", "Maisstärke"),
            CupWeight.Breadcrumbs to listOf("dry breadcrumbs", "plain bread crumbs", "Semmelbrösel", "Paniermehl"),
            CupWeight.Panko to listOf("panko", "panko breadcrumbs"),
            CupWeight.PeanutButter to listOf("creamy peanut butter", "Erdnussbutter"),
            CupWeight.Yogurt to listOf("plain yogurt", "Joghurt", "Naturjoghurt"),
            CupWeight.SourCream to listOf("sour cream", "saure Sahne", "Schmand"),
            CupWeight.CreamCheese to listOf("cream cheese, softened", "Frischkäse"),
        ).forEach { (weight, names) -> names.forEach { assertEquals(it, weight, CupWeight.of(it)) } }

    @Test
    fun `parentheses and prep after a comma don't matter`() {
        assertEquals(CupWeight.Flour, CupWeight.of("Mehl (Type 550)"))
        assertEquals(CupWeight.Flour, CupWeight.of("all-purpose flour, plus more for dusting"))
        assertEquals(CupWeight.Butter, CupWeight.of("(2 sticks) unsalted butter, cut into cubes"))
    }

    @Test
    fun `a note tells how the food is cut`() {
        assertEquals(CupWeight.SlicedAlmonds, CupWeight.of("almonds", "sliced"))
        assertEquals(CupWeight.ChoppedNuts, CupWeight.of("walnuts", "coarsely chopped"))
        assertEquals(CupWeight.Almonds, CupWeight.of("almonds", "toasted"))
        assertEquals(CupWeight.BrownSugar, CupWeight.of("brown sugar", "packed"))
    }

    @Test
    fun `foods whose weight per cup is uncertain or unknown match nothing`() =
        listOf(
            "kosher salt",
            "sea salt",
            "walnuts",
            "nuts",
            "almonds, chopped",
            "ground almonds",
            "almond flour",
            "gemahlene Mandeln",
            "sifted powdered sugar",
            "cooked rice",
            "brown rice",
            "steel-cut oats",
            "Greek yogurt",
            "fresh breadcrumbs",
            "buttermilk",
            "peanuts",
            "milk",
            "Milch",
            "heavy cream",
            "Sahne",
            "chicken broth",
            "white wine",
            "orange juice",
            "softened",
            "",
        ).forEach { assertNull(it, CupWeight.of(it)) }
}
