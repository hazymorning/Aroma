package app.aroma.core.model

import java.time.LocalTime

/**
 * The kinds of dish Mealie's meal plan knows, and the names households give them as categories or tags.
 * A recipe has no such field in Mealie: a category of one of these [names] says it. [hints] only suggest
 * a meal, as cake does the afternoon; the home tab finds such recipes for the time of day all the same.
 */
enum class Meal(private val names: List<String>, private val hints: Set<String> = emptySet()) {
    Breakfast(listOf("frühstück", "breakfast"), hints = setOf("brunch")),
    Lunch(listOf("mittagessen", "mittag", "lunch")),
    Snack(listOf("snack", "snacks", "zwischendurch", "zwischenmahlzeit"), hints = setOf("kuchen", "gebäck", "cake", "cakes")),
    Dinner(
        listOf("abendessen", "abendbrot", "dinner"),
        hints = setOf("hauptgericht", "hauptgerichte", "hauptspeise", "main", "main course", "mains"),
    ),
    Dessert(listOf("nachtisch", "dessert", "desserts", "nachspeise", "nachspeisen")),
    Side(listOf("beilage", "beilagen", "side", "sides", "side dish", "side dishes")),
    Drink(listOf("getränk", "getränke", "drink", "drinks", "beverage", "beverages")),
    ;

    /** The categories and tags among [organizers] named for this meal or hinting at it, as Mealie spells them. */
    fun namesIn(organizers: Organizers): List<String> =
        (organizers.categories + organizers.tags)
            .map { it.name }
            .filter { it.key() in names || it.key() in hints }
            .distinct()

    /** Whether [organizer] says a recipe is this meal: named for it, not merely hinting at it. */
    fun matches(organizer: Organizer): Boolean = organizer.name.key() in names

    /**
     * The category to file a recipe under for this meal: the group's own from [available], the one
     * called [label] first, or else a new one called [label], made when the recipe is saved.
     */
    fun categoryIn(available: List<Organizer>, label: String): Organizer {
        val own = available.filter(::matches)
        return own.firstOrNull { it.name.key() == label.key() }
            ?: own.minByOrNull { names.indexOf(it.name.key()) }
            ?: Organizer.new(label)
    }

    companion object {
        /** The meal the time of day calls for; one of the first four, which follow the day. */
        fun at(time: LocalTime): Meal =
            when (time.hour) {
                in 5..10 -> Breakfast
                in 11..13 -> Lunch
                in 14..16 -> Snack
                else -> Dinner
            }
    }
}

private fun String.key() = trim().lowercase()

/** What a recipe is filed under in Mealie: its categories and tags, the [meals] among them. */
data class Filing(val categories: List<Organizer>, val tags: List<Organizer>) {
    val meals: Set<Meal> get() = Meal.entries.filterTo(mutableSetOf()) { meal -> (categories + tags).any(meal::matches) }

    /**
     * Files the recipe under [meal] too, with its category from the group's [available] ones (see
     * [Meal.categoryIn]), or no longer: then every category and tag named for it goes.
     */
    fun toggle(meal: Meal, available: List<Organizer>, label: String): Filing =
        if (meal in meals) {
            Filing(categories.filterNot(meal::matches), tags.filterNot(meal::matches))
        } else {
            copy(categories = categories + meal.categoryIn(available, label))
        }
}
