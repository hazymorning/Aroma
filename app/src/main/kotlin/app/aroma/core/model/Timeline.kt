package app.aroma.core.model

import java.time.Instant

/** Something that happened to a recipe, as Mealie's timeline keeps it: added, cooked or commented on. */
data class TimelineEvent(
    val id: String,
    val recipe: RecipeSummary,
    val subject: String,
    val message: String?,
    val time: Instant,
)

data class TimelinePage(val events: List<TimelineEvent>, val hasMore: Boolean)
