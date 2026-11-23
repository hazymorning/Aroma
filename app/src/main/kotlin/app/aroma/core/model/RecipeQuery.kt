package app.aroma.core.model

enum class RecipeSort { Newest, Name, Rating, LastMade, Random }

enum class OrganizerKind { Category, Tag, Tool }

data class RecipeQuery(
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val cookbook: String? = null,
    /** Names of the categories and tags of one meal; a recipe in any of them matches. */
    val mealNames: List<String> = emptyList(),
    val sort: RecipeSort = RecipeSort.Newest,
    val seed: String? = null,
)

data class RecipePage(
    val items: List<RecipeSummary>,
    val total: Int,
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
