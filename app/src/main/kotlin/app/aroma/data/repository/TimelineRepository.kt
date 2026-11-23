package app.aroma.data.repository

import app.aroma.core.model.TimelinePage
import app.aroma.data.mealieCall
import app.aroma.data.remote.dto.CreateTimelineEventDto
import app.aroma.data.remote.dto.LastMadeDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** What happened to the household's recipes, as Mealie's timeline keeps it. */
class TimelineRepository(private val sessions: SessionManager, private val recipes: RecipeRepository) {
    private val changeCount = MutableStateFlow(0)

    /** Fires once an entry was written here, so the timeline loads again. */
    val changes: Flow<Unit> = changeCount.drop(1).map { }

    /** One page of events, newest first; events of recipes Aroma cannot see are left out. */
    suspend fun page(page: Int): TimelinePage =
        mealieCall {
            val result = sessions.api().timeline(page = page, perPage = PAGE_SIZE)
            val byId = recipes.summaries(result.items.mapTo(mutableSetOf()) { it.recipeId })
            TimelinePage(
                events = result.items.mapNotNull { event -> byId[event.recipeId]?.let(event::toModel) },
                hasMore = page < result.totalPages,
            )
        }

    /** Notes that a recipe was cooked, as Mealie's own "I made this" does: in the timeline and as its last-made date. */
    suspend fun cooked(recipeId: String, slug: String, subject: String) =
        mealieCall {
            val now = Instant.now().toString()
            val api = sessions.api()
            api.createTimelineEvent(CreateTimelineEventDto(recipeId, subject, eventType = "info", timestamp = now))
            api.markMade(slug, LastMadeDto(now))
            changeCount.update { it + 1 }
        }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
