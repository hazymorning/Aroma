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
