package app.aroma.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime

class MealTest {
    private fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())

    private val group = listOf(organizer("Hauptgericht"), organizer("Dinner"), organizer("Abendessen"), organizer("Kuchen"))

    @Test
    fun `choosing a kind of dish takes the group's category of its name, the one in the app's language first`() {
        val filed = Filing(emptyList(), emptyList()).toggle(Meal.Dinner, group, "Abendessen")

        assertEquals(listOf(organizer("Abendessen")), filed.categories)
        assertEquals(setOf(Meal.Dinner), filed.meals)
    }

    @Test
    fun `a category of another language serves where the group has none in the app's`() {
        val filed = Filing(emptyList(), emptyList()).toggle(Meal.Dinner, group - organizer("Abendessen"), "Abendessen")

        assertEquals(listOf(organizer("Dinner")), filed.categories)
    }

    @Test
    fun `without one in the group a new category is named in the app's language, and counts at once`() {
        val filed = Filing(listOf(organizer("Kuchen")), emptyList()).toggle(Meal.Dessert, group, "Nachtisch")

        assertEquals(listOf(organizer("Kuchen"), Organizer.new("Nachtisch")), filed.categories)
        assertTrue(filed.categories.last().isNew)
        assertEquals(setOf(Meal.Dessert), filed.meals)
    }

    @Test
    fun `taking a kind of dish off drops its categories and tags and keeps the rest`() {
        val filing = Filing(listOf(organizer("Dinner"), organizer("Hauptgericht")), listOf(organizer("abendessen"), organizer("Schnell")))

        val filed = filing.toggle(Meal.Dinner, group, "Abendessen")

        assertEquals(listOf(organizer("Hauptgericht")), filed.categories)
        assertEquals(listOf(organizer("Schnell")), filed.tags)
        assertEquals(emptySet<Meal>(), filed.meals)
    }

    @Test
    fun `names that only hint at a meal do not say what a recipe is, but still find it for the time of day`() {
        val filing = Filing(listOf(organizer("Hauptgericht"), organizer("Kuchen"), organizer("Brunch")), emptyList())

        assertEquals(emptySet<Meal>(), filing.meals)
        assertEquals(listOf("Hauptgericht"), Meal.Dinner.namesIn(Organizers(filing.categories)))
        assertEquals(listOf("Kuchen"), Meal.Snack.namesIn(Organizers(filing.categories)))
        assertEquals(listOf("Brunch"), Meal.Breakfast.namesIn(Organizers(filing.categories)))
    }

    @Test
    fun `the time of day still picks one of the four meals of a day`() {
        assertEquals(Meal.Breakfast, Meal.at(LocalTime.of(7, 0)))
        assertEquals(Meal.Lunch, Meal.at(LocalTime.of(12, 30)))
        assertEquals(Meal.Snack, Meal.at(LocalTime.of(15, 0)))
        assertEquals(Meal.Dinner, Meal.at(LocalTime.of(19, 0)))
    }

    /** A category made under a label must be read back as that kind of dish, in every language the app speaks. */
    @Test
    fun `every label names its kind of dish`() {
        val keys =
            mapOf(
                Meal.Breakfast to "meal_breakfast",
                Meal.Lunch to "meal_lunch",
                Meal.Snack to "meal_snack",
                Meal.Dinner to "meal_dinner",
                Meal.Dessert to "meal_dessert",
                Meal.Side to "meal_side",
                Meal.Drink to "meal_drink",
            )
        assertEquals(Meal.entries.toSet(), keys.keys)
        listOf("values", "values-de").forEach { folder ->
            val strings = File("src/main/res/$folder/strings.xml").readText()
            keys.forEach { (meal, key) ->
                val label = Regex("""<string name="$key">([^<]+)</string>""").find(strings)!!.groupValues[1]
                assertEquals("$folder: $label", setOf(meal), Filing(listOf(Organizer.new(label)), emptyList()).meals)
            }
        }
    }
}
