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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

class MealPlanRepository(private val sessions: SessionManager, private val cache: JsonCache) {
    private val changeCount = MutableStateFlow(0)

    /** Fires once an entry was added, moved or removed here, so copies of the plan kept elsewhere can follow. */
    val changes: Flow<Unit> = changeCount.drop(1).map { }

    /**
     * The entries of [days] days from [start] on. One copy is kept per length of range and filtered
     * to the days asked for, so offline on a new day the week cached the day before still shows.
     */
    fun range(start: LocalDate, days: Long): Flow<Loadable<List<MealPlanEntry>>> {
        val server = sessions.requireServerUrl()
        val end = start.plusDays(days - 1)
        return cache
            .stream("mealplan:$days", PlanCopy.serializer()) {
                PlanCopy(start.toString(), end.toString(), sessions.api().mealPlans(start.toString(), end.toString()).items)
            }.map { loadable ->
                val entries =
                    loadable.data.entries
                        .toEntries(server)
                        .filter { it.date in start..end }
                Loadable(entries, loadable.isRefreshing, loadable.error) to loadable.data.covers(start, end)
            }
            // A copy of other days has nothing here and would only flash an empty plan before the answer;
            // a copy of these very days with nothing planned is the plan as it is.
            .filterNot { (plan, covered) -> plan.isRefreshing && plan.data.isEmpty() && !covered }
            .map { (plan, _) -> plan }
    }

    /**
     * The week ahead as [refreshUpcoming] last fetched it, and again whenever that changes; null
     * before the first fetch. It is a copy of its own, so a week browsed in the app never takes its place.
     */
    fun upcoming(): Flow<List<MealPlanEntry>?> {
        val server = sessions.requireServerUrl()
        return cache.observe(UPCOMING_KEY, EntriesSerializer).map { it?.toEntries(server) }
    }

    /**
     * Fetches [today] and the [UPCOMING_DAYS] days after it for [upcoming]. An answer that arrives
     * after its sign-in ended is dropped, so the next account never sees that week.
     */
    suspend fun refreshUpcoming(today: LocalDate) {
        val signIn = sessions.current?.token
        val entries = mealieCall { sessions.api().mealPlans(today.toString(), today.plusDays(UPCOMING_DAYS).toString()).items }
        if (sessions.current?.token == signIn) cache.write(UPCOMING_KEY, EntriesSerializer, entries)
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
    }.also { changed() }

    suspend fun delete(id: Int) = mealieCall { sessions.api().deleteMealPlanEntry(id) }.also { changed() }

    /** Moves an entry to another day and meal. Mealie wants the whole entry back, its ids included. */
    suspend fun move(id: Int, date: LocalDate, type: MealType) =
        mealieCall {
            val api = sessions.api()
            val entry = api.mealPlanEntryJson(id) - "recipe"
            api.updateMealPlanEntry(
                id,
                JsonObject(entry + ("date" to JsonPrimitive(date.toString())) + ("entryType" to JsonPrimitive(type.apiValue))),
            )
        }.also { changed() }

    private fun changed() = changeCount.update { it + 1 }

    /** The plan for the days from [start] to [end] as Mealie had it. */
    @Serializable
    private data class PlanCopy(val start: String, val end: String, val entries: List<MealPlanEntryDto>) {
        fun covers(from: LocalDate, to: LocalDate): Boolean = LocalDate.parse(start) <= from && to <= LocalDate.parse(end)
    }

    private companion object {
        const val UPCOMING_KEY = "mealplan:upcoming"

        /** After dinner the widgets look at tomorrow, and a week from there still lies in the copy. */
        const val UPCOMING_DAYS = 7L

        val EntriesSerializer = ListSerializer(MealPlanEntryDto.serializer())

        fun List<MealPlanEntryDto>.toEntries(server: String): List<MealPlanEntry> =
            mapNotNull { it.toModel(server) }.sortedWith(compareBy({ it.date }, { it.type.ordinal }))
    }
}
