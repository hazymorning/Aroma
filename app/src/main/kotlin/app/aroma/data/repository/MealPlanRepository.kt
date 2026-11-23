package app.aroma.data.repository

import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.data.JsonCache
import app.aroma.data.Loadable
import app.aroma.data.mealieCall
import app.aroma.data.remote.dto.CreateMealPlanDto
import app.aroma.data.remote.dto.MealPlanEntryDto
import app.aroma.data.remote.toModel
import app.aroma.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

class MealPlanRepository(private val sessions: SessionManager, private val cache: JsonCache) {
    /**
     * The entries of [days] days from [start] on. One copy is kept per length of range and filtered
     * to the days asked for, so offline on a new day the week cached the day before still shows.
     */
    fun range(start: LocalDate, days: Long): Flow<Loadable<List<MealPlanEntry>>> {
        val server = sessions.requireServerUrl()
        val end = start.plusDays(days - 1)
        return cache
            .stream("mealplan:$days", ListSerializer(MealPlanEntryDto.serializer())) {
                sessions.api().mealPlans(start.toString(), end.toString()).items
            }.map { loadable ->
                val entries =
                    loadable.data
                        .mapNotNull { it.toModel(server) }
                        .filter { it.date in start..end }
                        .sortedWith(compareBy({ it.date }, { it.type.ordinal }))
                Loadable(entries, loadable.isRefreshing, loadable.error)
            }
            // A copy of other days has nothing here and would only flash an empty plan before the answer.
            .filterNot { it.isRefreshing && it.data.isEmpty() }
    }

    suspend fun add(
        date: LocalDate,
        type: MealType,
        recipeId: String?,
        title: String,
        note: String,
    ) = mealieCall {
        sessions.api().createMealPlanEntry(
            CreateMealPlanDto(
                date = date.toString(),
                entryType = type.apiValue,
                title = title.trim(),
                text = note.trim(),
                recipeId = recipeId,
            ),
        )
    }

    suspend fun delete(id: Int) = mealieCall { sessions.api().deleteMealPlanEntry(id) }

    /** Moves an entry to another day and meal. Mealie wants the whole entry back, its ids included. */
    suspend fun move(id: Int, date: LocalDate, type: MealType) =
        mealieCall {
            val api = sessions.api()
            val entry = api.mealPlanEntryJson(id) - "recipe"
            api.updateMealPlanEntry(
                id,
                JsonObject(entry + ("date" to JsonPrimitive(date.toString())) + ("entryType" to JsonPrimitive(type.apiValue))),
            )
        }
}
