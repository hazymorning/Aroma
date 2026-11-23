package app.aroma.core.model

import java.time.LocalTime

/** The meal a time of day calls for, and the names households give it as a category or tag in Mealie. */
enum class Meal(private val names: Set<String>) {
    Breakfast(setOf("frühstück", "breakfast", "brunch")),
    Lunch(setOf("mittagessen", "mittag", "lunch")),
    Snack(setOf("snack", "snacks", "zwischendurch", "kuchen", "gebäck", "cake", "cakes")),
    Dinner(setOf("abendessen", "abendbrot", "dinner", "hauptgericht", "hauptgerichte", "hauptspeise", "main", "main course", "mains")),
    ;

    /** The categories and tags among [organizers] named for this meal, as Mealie spells them. */
    fun namesIn(organizers: Organizers): List<String> =
        (organizers.categories + organizers.tags).map { it.name }.filter { it.trim().lowercase() in names }.distinct()

    companion object {
        fun at(time: LocalTime): Meal =
            when (time.hour) {
                in 5..10 -> Breakfast
                in 11..13 -> Lunch
                in 14..16 -> Snack
                else -> Dinner
            }
    }
}
