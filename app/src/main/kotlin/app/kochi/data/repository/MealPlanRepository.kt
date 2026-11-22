package app.kochi.data.repository

import app.kochi.core.model.MealPlanEntry
import app.kochi.core.model.MealType
import app.kochi.data.JsonCache
import app.kochi.data.Loadable
import app.kochi.data.mealieCall
import app.kochi.data.remote.dto.CreateMealPlanDto
import app.kochi.data.remote.dto.MealPlanEntryDto
import app.kochi.data.remote.toModel
import app.kochi.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

class MealPlanRepository(private val sessions: SessionManager, private val cache: JsonCache) {
    fun range(start: LocalDate, days: Long): Flow<Loadable<List<MealPlanEntry>>> {
        val server = sessions.requireServerUrl()
        val end = start.plusDays(days - 1)
        return cache
            .stream("mealplan:$start:$days", ListSerializer(MealPlanEntryDto.serializer())) {
                sessions.api().mealPlans(start.toString(), end.toString()).items
            }.map { loadable ->
                val entries = loadable.data.mapNotNull { it.toModel(server) }.sortedWith(compareBy({ it.date }, { it.type.ordinal }))
                Loadable(entries, loadable.isRefreshing, loadable.error)
            }
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
}
