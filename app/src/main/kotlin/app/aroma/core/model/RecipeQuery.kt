package app.aroma.core.model

enum class RecipeSort { Newest, Name, Rating, LastMade, Random }

enum class OrganizerKind { Category, Tag }

/** What counts as quick: on the table in this many minutes or less, all in; the titles that name it say so too. */
const val QUICK_MINUTES = 30

data class RecipeQuery(
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val cookbook: String? = null,
    /** Names of the categories and tags of one meal; a recipe in any of them matches. */
    val mealNames: List<String> = emptyList(),
    /** The most minutes a recipe may take all in. Mealie keeps times as text, so the phone picks these. */
    val readyWithin: Int? = null,
    /** Only recipes cooked before, but not since, this day. */
    val cookedBefore: java.time.LocalDate? = null,
    val sort: RecipeSort = RecipeSort.Newest,
    val seed: String? = null,
)

data class RecipePage(
    val items: List<RecipeSummary>,
    /** How many recipes match in all; null where the phone picks them page by page and cannot know. */
    val total: Int?,
    val page: Int,
    val hasMore: Boolean,
    val isOffline: Boolean,
)

data class Organizers(
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val tools: List<Organizer> = emptyList(),
)

data class UserRating(val rating: Double?, val isFavorite: Boolean)
