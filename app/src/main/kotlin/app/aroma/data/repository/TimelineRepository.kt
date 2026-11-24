package app.aroma.data.repository

import app.aroma.data.mealieCall
import app.aroma.data.remote.dto.CreateTimelineEventDto
import app.aroma.data.remote.dto.LastMadeDto
import app.aroma.data.session.SessionManager
import java.time.Instant

/** What Aroma writes into Mealie's timeline of the household's recipes. */
class TimelineRepository(private val sessions: SessionManager) {
    /** Notes that a recipe was cooked, as Mealie's own "I made this" does: in the timeline and as its last-made date. */
    suspend fun cooked(recipeId: String, slug: String, subject: String) =
        mealieCall {
            val now = Instant.now().toString()
            val api = sessions.api()
            api.createTimelineEvent(CreateTimelineEventDto(recipeId, subject, eventType = "info", timestamp = now))
            api.markMade(slug, LastMadeDto(now))
        }
}
