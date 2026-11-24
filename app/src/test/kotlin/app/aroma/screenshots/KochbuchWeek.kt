package app.aroma.screenshots

import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.feature.mealplan.MealPlanDay
import java.time.LocalDate

/**
 * A week and a shopping list with the brief's test texts (section 14): today is Tuesday, 24
 * November 2026; a second week runs across the turn of the month, 30 November to 6 December.
 */
object KochbuchWeek {
    val today: LocalDate = LocalDate.of(2026, 11, 24)
    val monday: LocalDate = LocalDate.of(2026, 11, 23)

    /** The week across the turn of the month. */
    val nextMonday: LocalDate = LocalDate.of(2026, 11, 30)

    private val recipes = KochbuchSamples.summaries

    private fun entry(
        id: Int,
        day: Long,
        type: MealType,
        recipe: Int?,
        title: String = "",
        note: String = "",
        from: LocalDate = monday,
    ) = MealPlanEntry(id, from.plusDays(day), type, title, note, recipe?.let { recipes[it] })

    /** Monday past with two meals, today with a long title and a short one, two empty days, a full Saturday. */
    val entries =
        listOf(
            entry(1, 0, MealType.Lunch, null, title = "Reste vom Sonntag", note = "Was der Kühlschrank hergibt"),
            entry(2, 0, MealType.Dinner, 3),
            entry(3, 1, MealType.Lunch, 6),
            entry(4, 1, MealType.Dinner, 1),
            entry(5, 3, MealType.Dinner, 2),
            entry(6, 5, MealType.Snack, 0),
            entry(7, 5, MealType.Dessert, 4),
            entry(8, 6, MealType.Dinner, 5),
        )

    val days: List<MealPlanDay> = days(monday, entries)

    val emptyDays: List<MealPlanDay> = days(monday, emptyList())

    /** Two meals in the week from 30 November to 6 December. */
    val turnOfMonth: List<MealPlanDay> =
        days(
            nextMonday,
            listOf(
                entry(11, 1, MealType.Dinner, 1, from = nextMonday),
                entry(12, 3, MealType.Lunch, 2, from = nextMonday),
            ),
        )

    private fun days(from: LocalDate, entries: List<MealPlanEntry>) =
        (0L until 7L).map { offset -> from.plusDays(offset).let { day -> MealPlanDay(day, entries.filter { it.date == day }) } }

    private val produce = ShoppingLabel("produce", "Obst & Gemüse")
    private val dairy = ShoppingLabel("dairy", "Milchprodukte")
    private val bakery = ShoppingLabel("bakery", "Backwaren")
    private val pantry = ShoppingLabel("pantry", "Vorrat")

    private val goods =
        listOf(
            "2 Bio-Zitronen" to produce,
            "1 kg Rhabarber" to produce,
            "500 g Erdbeeren" to produce,
            "1 Hokkaido-Kürbis" to produce,
            "2 Auberginen" to produce,
            "1 Bund Koriander" to produce,
            "3 Frühlingszwiebeln" to produce,
            "1 Knolle Ingwer" to produce,
            "200 g Feta" to dairy,
            "500 g Kokos-Joghurt" to dairy,
            "250 g Butter" to dairy,
            "10 Eier" to dairy,
            "400 ml Sahne" to dairy,
            "250 g Mascarpone" to dairy,
            "1 l Milch" to dairy,
            "6 Weizen-Tortillas" to bakery,
            "1 Packung Löffelbiskuits" to bakery,
            "1 Bauernbrot" to bakery,
            "500 g Mehl" to pantry,
            "250 g Zucker" to pantry,
            "150 g Puderzucker" to pantry,
            "1 Päckchen Backpulver" to pantry,
            "200 g Couscous" to pantry,
            "400 ml Kokosmilch" to pantry,
            "2 EL Tikka-Masala-Paste" to pantry,
            "100 g Mandelblättchen" to pantry,
            "1 Glas Honig" to pantry,
            "Salz" to pantry,
            "1 Flasche BBQ-Soße" to pantry,
            "50 g Pistazien" to pantry,
            "Speisestärke" to pantry,
            "1 Dose Kichererbsen" to pantry,
            "Vanilleextrakt" to pantry,
            "Zimt" to pantry,
            "Küchenrolle" to null,
            "Backpapier" to null,
            "Spülmittel" to null,
            "Gefrierbeutel" to null,
            "Alufolie" to null,
            "Kerzen" to null,
        )

    /** Forty things to buy in four groups and some without, three of them ticked off. */
    val longList =
        ShoppingList(
            id = "list",
            name = "Wocheneinkauf",
            items =
                goods.mapIndexed { index, (note, label) ->
                    ShoppingItem("i$index", "list", 0.0, null, null, note, index % 13 == 5, index, label, emptyList())
                },
            labelOrder = listOf("produce", "dairy", "bakery", "pantry"),
        )

    val emptyShoppingList = longList.copy(items = emptyList())
}
