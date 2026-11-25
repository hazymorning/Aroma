package app.aroma.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ServerSocketFactory
import kotlin.random.Random

/**
 * Mealie in memory: it keeps recipes, ratings, the meal plan, shopping lists and cookbooks, and
 * answers what Aroma asks in the shape of Mealie 3.31's real answers, recorded under
 * resources/mealie, so every test also checks that Aroma reads what Mealie sends. A test fills it,
 * makes it slow, failing, silent or refusing where it needs to, and reads in [requests] what Aroma sent.
 */
class FakeMealie : Closeable {
    private val server = MockWebServer()
    private val log = CopyOnWriteArrayList<Request>()
    private val holds = CopyOnWriteArrayList<Hold>()
    private val failures = mutableMapOf<String, Int>()
    private val quiet = CountDownLatch(1)

    /** What Aroma sent, in order. */
    val requests: List<Request> get() = log

    /** Requests the fake could not make sense of; a test that leaves any fails, as the fake would only have guessed. */
    val problems = CopyOnWriteArrayList<String>()

    /**
     * Each page after the first starts this many recipes before the end of the one before, as when
     * recipes are added at the top while Aroma pages through.
     */
    @Volatile var pageOverlap = 0

    /** Takes every request and answers none, as a server the phone's network no longer reaches; what it took stays unanswered. */
    @Volatile var silent = false

    /** Drops every connection at once, as a server that is down. */
    @Volatile var refusing = false

    private val user: JsonObject = Recorded.user
    val userId: String = user.string("id")
    val email: String = user.string("email")
    private val tokens = mutableSetOf<String>()
    private val apiTokens = mutableMapOf<Int, String>()
    private var serial = 0

    private val recipes = mutableListOf<Recipe>()
    private val ratings = mutableMapOf<String, Rating>()
    private val categories = mutableListOf<Organizer>()
    private val tags = mutableListOf<Organizer>()
    private val cookbooks = mutableListOf<Cookbook>()
    private val plan = mutableListOf<PlanEntry>()
    private val lists = mutableListOf<ShoppingList>()

    init {
        server.serverSocketFactory = NoDelaySockets
        server.dispatcher = Answering()
        server.start()
    }

    val url: String = server.url("/").toString().trimEnd('/')

    data class Organizer(val id: String, val name: String, val slug: String)

    data class Recipe(
        val id: String,
        val slug: String,
        val name: String,
        val totalTime: String?,
        val prepTime: String?,
        val performTime: String?,
        val categories: List<Organizer>,
        val tags: List<Organizer>,
        /** What the household's other members gave it, which Mealie's average counts with the user's own. */
        val othersRatings: List<Double>,
        val createdAt: Instant,
        val lastMade: LocalDate?,
    )

    /** The signed-in user's own rating, 0.0 once taken back, as Mealie keeps it. */
    data class Rating(val rating: Double?, val isFavorite: Boolean)

    data class PlanEntry(
        val id: Int,
        val date: LocalDate,
        val entryType: String,
        val recipeId: String?,
    )

    data class Cookbook(val id: String, val name: String, val recipeIds: Set<String>)

    class ShoppingList(val id: String, val name: String) {
        val items = mutableListOf<JsonObject>()
    }

    /** One request as Aroma sent it; [token] is the one it was signed with. */
    class Request(
        val method: String,
        val url: HttpUrl,
        val token: String?,
        val body: String,
    ) {
        val path: String get() = url.encodedPath

        override fun toString() = "$method ${url.encodedPath}${url.encodedQuery?.let { "?$it" }.orEmpty()}"
    }

    fun requests(method: String, path: String): List<Request> = log.filter { it.method == method && it.path == path }

    fun addRecipe(
        name: String,
        totalTime: String? = null,
        prepTime: String? = null,
        performTime: String? = null,
        categories: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        othersRatings: List<Double> = emptyList(),
        lastMade: LocalDate? = null,
    ): Recipe =
        synchronized(this) {
            val slug = slugOf(name)
            Recipe(
                id = UUID.nameUUIDFromBytes(slug.toByteArray()).toString(),
                slug = slug,
                name = name,
                totalTime = totalTime,
                prepTime = prepTime,
                performTime = performTime,
                categories = categories.map { organizer(this.categories, it) },
                tags = tags.map { organizer(this.tags, it) },
                othersRatings = othersRatings,
                // Apart in time, so the newest first is the last added first.
                createdAt = FIRST_ADDED.plusSeconds((++serial).toLong()),
                lastMade = lastMade,
            ).also { recipes += it }
        }

    fun rate(recipe: Recipe, stars: Double?, favorite: Boolean = false) =
        synchronized(this) { ratings[recipe.id] = Rating(stars, favorite) }

    fun ratingOf(recipe: Recipe): Rating? = synchronized(this) { ratings[recipe.id] }

    fun recipeNamed(name: String): Recipe? = synchronized(this) { recipes.firstOrNull { it.name == name } }

    fun addCookbook(name: String, vararg recipes: Recipe): Cookbook =
        synchronized(this) {
            Cookbook(UUID.randomUUID().toString(), name, recipes.mapTo(mutableSetOf()) { it.id }).also { cookbooks += it }
        }

    fun plan(date: LocalDate, recipe: Recipe, entryType: String = "dinner"): PlanEntry =
        synchronized(this) { PlanEntry(++serial, date, entryType, recipe.id).also { plan += it } }

    val planned: List<PlanEntry> get() = synchronized(this) { plan.toList() }

    /** A shopping list holding [notes], each an item of its own, those in [checked] in the basket; returns its id. */
    fun addList(name: String, vararg notes: String, checked: Set<String> = emptySet()): String =
        synchronized(this) {
            val list = ShoppingList(UUID.randomUUID().toString(), name)
            notes.forEachIndexed { position, note ->
                list.items += item(list.id, note, position).with("checked" to (note in checked))
            }
            lists += list
            list.id
        }

    /** The items of list [listId] as Mealie keeps them. */
    fun items(listId: String): List<JsonObject> = synchronized(this) { lists.first { it.id == listId }.items.toList() }

    /** Requests to [path] wait for [Hold.release]; with [method], only those. */
    fun hold(path: String, method: String? = null): Hold = Hold(path) { method == null || it == method }.also { holds += it }

    /** Requests to [path] get Mealie's error [code]. */
    fun fail(path: String, code: Int) = synchronized(this) { failures[path] = code }

    /** Answers requests to its path once released. */
    class Hold internal constructor(private val path: String, private val method: (String) -> Boolean) {
        private val gate = CountDownLatch(1)
        private val arrived = AtomicInteger()

        /** How many requests came to it so far. */
        val waiting: Int get() = arrived.get()

        internal fun matches(request: Request) = request.path == path && method(request.method)

        internal fun await() {
            arrived.incrementAndGet()
            gate.await()
        }

        fun release() = gate.countDown()
    }

    override fun close() {
        quiet.countDown()
        holds.forEach { it.release() }
        server.close()
    }

    private inner class Answering : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val token = request.headers["Authorization"]?.removePrefix("Bearer ")
            val call = Request(request.method, request.url, token, request.body?.utf8().orEmpty())
            log += call
            if (refusing) return dropped()
            if (silent) {
                quiet.await()
                return dropped()
            }
            holds.filter { it.matches(call) }.forEach { it.await() }
            return synchronized(this@FakeMealie) { failures[call.path]?.let { errorAnswer(it) } ?: answer(call) }
        }
    }

    /** Mealie's answer to [call]; a list it is asked for that no test fills is empty, as Mealie has none of them. */
    private fun answer(call: Request): MockResponse {
        val method = call.method
        val path = call.path.removePrefix("/api/")
        val parts = path.split('/')
        return when {
            method == "GET" && path == "app/about" -> json(Recorded.about)
            method == "POST" && path == "auth/token" -> signIn(call.body)
            call.token !in tokens -> errorAnswer(UNAUTHORIZED)
            method == "GET" && path == "users/self" -> json(user)
            method == "POST" && path == "users/api-tokens" -> newApiToken(call.json())
            method == "DELETE" && parts.matches("users", "api-tokens", "*") -> revoke(parts[2].toInt())
            method == "GET" && path == "users/self/ratings" -> json(ratingsJson())
            method == "POST" && parts.matches("users", "*", "ratings", "*") -> rate(parts[3], call.json())
            method == "GET" && path == "groups/self" -> json(Recorded.group)
            method == "GET" && path == "recipes" -> recipesPage(call)
            method == "GET" && parts.matches("recipes", "*") -> recipe(parts[1])?.let { json(recipeJson(it)) } ?: errorAnswer(NOT_FOUND)
            method == "DELETE" && parts.matches("recipes", "*") -> deleteRecipe(parts[1])
            method == "GET" && path == "organizers/categories" -> json(page(categories.map(::organizerJson)))
            method == "GET" && path == "organizers/tags" -> json(page(tags.map(::organizerJson)))
            method == "GET" && path == "households/cookbooks" -> json(page(cookbooks.mapIndexed(::cookbookJson)))
            method == "GET" && path == "households/mealplans" -> mealPlans(call.url)
            method == "POST" && path == "households/mealplans" -> addPlanned(call.json())
            method == "DELETE" && parts.matches("households", "mealplans", "*") -> deleted { plan.removeAll { it.id == parts[2].toInt() } }
            method == "GET" && path == "households/shopping/lists" -> json(page(lists.map { listJson(it, Recorded.listSummary) }))
            method == "GET" && parts.matches("households", "shopping", "lists", "*") -> shoppingList(parts[3])
            method == "DELETE" && parts.matches("households", "shopping", "lists", "*") -> deleted { lists.removeAll { it.id == parts[3] } }
            method == "POST" && path == "households/shopping/items" -> saveItem(call.json(), creating = true)
            method == "PUT" && parts.matches("households", "shopping", "items", "*") -> saveItem(call.json(), creating = false)
            method == "DELETE" && parts.matches("households", "shopping", "items", "*") -> removeItem(parts[3])
            method == "GET" && parts.last().matches(Regex("[a-z-]+")) -> json(page(emptyList()))
            else -> notModelled(call)
        }
    }

    /** Mealie answers the removal of an item it no longer has as if it had removed it. */
    private fun removeItem(id: String) = deleted { lists.forEach { it.remove(id) } }

    private fun ratingsJson() = Recorded.ratings.with("ratings" to ratings.map { (id, rating) -> ratingJson(id, rating) })

    private fun signIn(form: String): MockResponse {
        val fields = form.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val known = fields["username"] == email || fields["username"] == user.string("username")
        if (!known || fields["password"] != PASSWORD) return json(Recorded.wrongPassword, UNAUTHORIZED)
        val token = "session-token-${++serial}"
        tokens += token
        return json(Recorded.token.with("access_token" to token))
    }

    private fun newApiToken(body: JsonObject): MockResponse {
        val id = ++serial
        val token = "api-token-$id"
        apiTokens[id] = token
        tokens += token
        return json(Recorded.apiToken.with("name" to body.stringOrNull("name"), "id" to id, "token" to token), CREATED)
    }

    private fun revoke(id: Int) = deleted { apiTokens.remove(id)?.let { tokens -= it } }

    private fun rate(slug: String, body: JsonObject): MockResponse {
        val recipe = recipe(slug) ?: return errorAnswer(NOT_FOUND)
        val before = ratings[recipe.id] ?: Rating(null, false)
        val stars = if ("rating" in body) body.getValue("rating").jsonPrimitive.doubleOrNull else before.rating
        ratings[recipe.id] = Rating(stars, body["isFavorite"]?.jsonPrimitive?.booleanOrNull ?: before.isFavorite)
        return json(JsonNull)
    }

    private fun recipesPage(call: Request): MockResponse {
        val url = call.url
        val filter = url.queryParameter("queryFilter")?.let { query -> filterFrom(query) ?: return notModelled(call) }
        val search = url.queryParameter("search")?.lowercase()
        val wantedCategories = url.queryParameterValues("categories").filterNotNull()
        val wantedTags = url.queryParameterValues("tags").filterNotNull()
        val cookbook = url.queryParameter("cookbook")?.let { id -> cookbooks.firstOrNull { it.id == id } }
        val found =
            recipes.filter { recipe ->
                (search == null || search in recipe.name.lowercase()) &&
                    (wantedCategories.isEmpty() || recipe.categories.any { it.id in wantedCategories || it.slug in wantedCategories }) &&
                    (wantedTags.isEmpty() || recipe.tags.any { it.id in wantedTags || it.slug in wantedTags }) &&
                    (cookbook == null || recipe.id in cookbook.recipeIds) &&
                    (filter == null || filter(recipe))
            }
        val ascending = url.queryParameter("orderDirection") != "desc"
        val ordered =
            when (url.queryParameter("orderBy")) {
                "random" -> found.shuffled(Random(url.queryParameter("paginationSeed").hashCode()))
                "name" -> found.sortedBy { it.name.lowercase() }.inOrder(ascending)
                "rating" -> found.sortedWith(compareBy(nullsFirst()) { average(it) }).inOrder(ascending)
                "last_made" -> found.sortedWith(compareBy(nullsFirst()) { it.lastMade }).inOrder(ascending)
                else -> found.sortedBy { it.createdAt }.inOrder(ascending)
            }
        val number = url.queryParameter("page")?.toInt() ?: 1
        val perPage = url.queryParameter("perPage")?.toInt() ?: DEFAULT_PER_PAGE
        val size = if (perPage < 0) ordered.size else perPage
        val start = ((number - 1) * (size - pageOverlap)).coerceAtLeast(0)
        return json(page(ordered.drop(start).take(size).map(::summaryJson), number, size, ordered.size))
    }

    private fun List<Recipe>.inOrder(ascending: Boolean) = if (ascending) this else reversed()

    /** Mealie's query filter, in the forms Aroma writes; null for any other. */
    private fun filterFrom(query: String): ((Recipe) -> Boolean)? {
        val clauses = query.removePrefix("(").removeSuffix(")").split(") AND (")
        val tests =
            clauses.map { clause ->
                IdsFilter.matchEntire(clause)?.let { match ->
                    val ids = quoted(match.groupValues[1])
                    return@map { recipe: Recipe -> recipe.id in ids }
                }
                NamesFilter.matchEntire(clause)?.let { match ->
                    val names = quoted(match.groupValues[1])
                    return@map { recipe: Recipe -> (recipe.categories + recipe.tags).any { it.name in names } }
                }
                MadeFilter.matchEntire(clause)?.let { match ->
                    val (after, before) = match.destructured
                    return@map { recipe: Recipe -> recipe.lastMade?.toString()?.let { it > after && it < before } == true }
                }
                return null
            }
        return { recipe -> tests.all { it(recipe) } }
    }

    private fun quoted(list: String): Set<String> = Regex("\"([^\"]*)\"").findAll(list).mapTo(mutableSetOf()) { it.groupValues[1] }

    private fun deleteRecipe(slug: String): MockResponse {
        val recipe = recipe(slug) ?: return errorAnswer(NOT_FOUND)
        recipes.remove(recipe)
        ratings.remove(recipe.id)
        return json(recipeJson(recipe))
    }

    private fun mealPlans(url: HttpUrl): MockResponse {
        val days = LocalDate.parse(url.queryParameter("start_date"))..LocalDate.parse(url.queryParameter("end_date"))
        return json(page(plan.filter { it.date in days }.sortedBy { it.date }.map(::planJson)))
    }

    private fun addPlanned(body: JsonObject): MockResponse {
        val entry = PlanEntry(++serial, LocalDate.parse(body.string("date")), body.string("entryType"), body.stringOrNull("recipeId"))
        plan += entry
        return json(planJson(entry), CREATED)
    }

    private fun shoppingList(id: String): MockResponse {
        val list = lists.firstOrNull { it.id == id } ?: return errorAnswer(NOT_FOUND)
        return json(listJson(list, Recorded.list))
    }

    /**
     * Mealie takes a new item with the id of one it has as a change to that one, and answers a
     * change to one it no longer has with nothing done.
     */
    private fun saveItem(body: JsonObject, creating: Boolean): MockResponse {
        val id = body.stringOrNull("id") ?: UUID.randomUUID().toString()
        val list = lists.firstOrNull { list -> list.items.any { it.string("id") == id } }
        if (list != null) {
            val index = list.items.indexOfFirst { it.string("id") == id }
            list.items[index] = JsonObject(list.items[index] + body)
            return json(itemsAnswer(updated = list.items[index]), if (creating) CREATED else OK)
        }
        val target = lists.firstOrNull { it.id == body.stringOrNull("shoppingListId") }
        if (!creating || target == null) return json(itemsAnswer())
        val created = JsonObject(Recorded.item + body).with("id" to id, "display" to body.stringOrNull("note"))
        target.items += created
        return json(itemsAnswer(created = created), CREATED)
    }

    private fun ShoppingList.remove(itemId: String) = items.removeAll { it.string("id") == itemId }

    private fun item(listId: String, note: String, position: Int): JsonObject {
        val id = UUID.randomUUID().toString()
        return Recorded.item.with("id" to id, "shoppingListId" to listId, "note" to note, "display" to note, "position" to position)
    }

    private fun itemsAnswer(created: JsonObject? = null, updated: JsonObject? = null) =
        Recorded.itemsAnswer.with(
            "createdItems" to listOfNotNull(created),
            "updatedItems" to listOfNotNull(updated),
            "deletedItems" to emptyList<JsonObject>(),
        )

    private fun summaryJson(recipe: Recipe): JsonObject = recipeFields(Recorded.summary, recipe)

    private fun recipeJson(recipe: Recipe): JsonObject = recipeFields(Recorded.recipe, recipe)

    private fun recipeFields(template: JsonObject, recipe: Recipe): JsonObject {
        val stamp = recipe.createdAt.toString()
        return template.with(
            "id" to recipe.id,
            "userId" to userId,
            "name" to recipe.name,
            "slug" to recipe.slug,
            "totalTime" to recipe.totalTime,
            "prepTime" to recipe.prepTime,
            "performTime" to recipe.performTime,
            "description" to "",
            "recipeCategory" to recipe.categories.map(::organizerJson),
            "tags" to recipe.tags.map(::organizerJson),
            "rating" to average(recipe),
            "orgURL" to null,
            "dateAdded" to LocalDate.ofInstant(recipe.createdAt, ZoneOffset.UTC).toString(),
            "dateUpdated" to stamp,
            "createdAt" to stamp,
            "updatedAt" to stamp,
        )
    }

    /** Mealie's average leaves out ratings taken back, which it keeps as 0. */
    private fun average(recipe: Recipe): Double? {
        val counted = (recipe.othersRatings + listOfNotNull(ratings[recipe.id]?.rating)).filter { it > 0 }
        return counted.takeIf { it.isNotEmpty() }?.average()
    }

    private fun ratingJson(recipeId: String, rating: Rating) =
        Recorded.rating.with("recipeId" to recipeId, "rating" to rating.rating, "isFavorite" to rating.isFavorite)

    private fun organizerJson(organizer: Organizer) =
        Recorded.organizer.with("id" to organizer.id, "name" to organizer.name, "slug" to organizer.slug)

    private fun cookbookJson(position: Int, book: Cookbook) =
        Recorded.cookbook.with("id" to book.id, "name" to book.name, "slug" to slugOf(book.name), "position" to position)

    private fun planJson(entry: PlanEntry) =
        Recorded.planEntry.with(
            "id" to entry.id,
            "date" to entry.date.toString(),
            "entryType" to entry.entryType,
            "recipeId" to entry.recipeId,
            "recipe" to recipes.firstOrNull { it.id == entry.recipeId }?.let(::summaryJson),
            "userId" to userId,
        )

    /** A list in the shape of [template]; Aroma puts recipes' ingredients on lists, but no test needs Mealie to say which. */
    private fun listJson(list: ShoppingList, template: JsonObject) =
        template.with(
            "id" to list.id,
            "name" to list.name,
            "listItems" to list.items.toList(),
            "recipeReferences" to emptyList<JsonObject>(),
        )

    private fun page(items: List<JsonObject>, number: Int = 1, perPage: Int = items.size, total: Int = items.size) =
        Recorded.page.with(
            "page" to number,
            "per_page" to perPage,
            "total" to total,
            "total_pages" to if (perPage <= 0) 0 else (total + perPage - 1) / perPage,
            "items" to items,
        )

    private fun recipe(slug: String) = recipes.firstOrNull { it.slug == slug || it.id == slug }

    private fun organizer(known: MutableList<Organizer>, name: String) =
        known.firstOrNull { it.name == name } ?: Organizer(UUID.randomUUID().toString(), name, slugOf(name)).also { known += it }

    private fun deleted(change: () -> Unit): MockResponse {
        change()
        return json(Recorded.deleted)
    }

    private fun notModelled(call: Request): MockResponse {
        problems += "$call"
        return errorAnswer(NOT_FOUND)
    }

    private fun dropped() = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

    private fun errorAnswer(code: Int) = json(if (code == UNAUTHORIZED) Recorded.unauthorized else Recorded.notFound, code)

    private fun json(body: JsonElement, code: Int = OK) =
        MockResponse
            .Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body.toString())
            .build()

    private fun Request.json(): JsonObject = Json.parseToJsonElement(body).jsonObject

    companion object {
        const val PASSWORD = "MyPassword"
        private const val OK = 200
        private const val CREATED = 201
        private const val UNAUTHORIZED = 401
        private const val NOT_FOUND = 404
        private const val DEFAULT_PER_PAGE = 50
        private val FIRST_ADDED: Instant = Instant.parse("2026-10-01T08:00:00Z")
        private val IdsFilter = Regex("""^id IN \[(.*)]$""")
        private val NamesFilter = Regex("""^recipe_category\.name IN \[(.*)] OR tags\.name IN \[.*]$""")
        private val MadeFilter = Regex("""^lastMade > "(.+)" AND lastMade < "(.+)"$""")

        private fun slugOf(name: String): String = name.lowercase().replace(Regex("[^a-z0-9äöüß]+"), "-").trim('-')
    }
}

/** Mealie 3.31's answers as recorded from a real server; the fake answers in their shape. */
private object Recorded {
    private fun load(name: String): JsonObject {
        val text = FakeMealie::class.java.getResource("/mealie/$name.json")!!.readText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.first(field: String) = getValue(field).jsonArray[0].jsonObject

    val about = load("about")
    val token = load("token")
    val user = load("user")
    val apiToken = load("api-token")
    val group = load("group")
    val page = load("recipes")
    val summary = page.first("items")
    val recipe = load("recipe")
    val ratings = load("ratings")
    val rating = ratings.first("ratings")
    val organizer = load("categories").first("items")
    val cookbook = load("cookbooks").first("items")
    val planEntry = load("mealplans").first("items")
    val listSummary = load("shopping-lists").first("items")
    val list = load("shopping-list")
    val itemsAnswer = load("shopping-items")
    val item = itemsAnswer.first("createdItems")

    /** How Mealie answers a deletion, a wrong password, a token it does not take, and something it does not have. */
    val deleted = Json.parseToJsonElement("""{"message":"","error":false}""")
    val wrongPassword = Json.parseToJsonElement("""{"detail":"Unauthorized"}""")
    val unauthorized = Json.parseToJsonElement("""{"detail":"Could not validate credentials"}""")
    val notFound = Json.parseToJsonElement("""{"detail":{"message":"No Entry Found","error":true,"exception":null}}""")
}

/** Answers on loopback come at once only without Nagle's algorithm, which otherwise waits ~40 ms for a delayed ACK each time. */
private object NoDelaySockets : ServerSocketFactory() {
    override fun createServerSocket(): ServerSocket = NoDelayServerSocket()

    override fun createServerSocket(port: Int): ServerSocket = NoDelayServerSocket().also { it.bind(InetSocketAddress(port)) }

    override fun createServerSocket(port: Int, backlog: Int): ServerSocket =
        NoDelayServerSocket().also { it.bind(InetSocketAddress(port), backlog) }

    override fun createServerSocket(port: Int, backlog: Int, address: InetAddress?): ServerSocket =
        NoDelayServerSocket().also { it.bind(InetSocketAddress(address, port), backlog) }
}

private class NoDelayServerSocket : ServerSocket() {
    override fun accept(): Socket = super.accept().apply { tcpNoDelay = true }
}

/** Whether a path's parts are [pattern]'s, where "*" stands for any one part. */
private fun List<String>.matches(vararg pattern: String) =
    size == pattern.size && zip(pattern).all { (part, wanted) -> wanted == "*" || part == wanted }

private fun JsonObject.with(vararg fields: Pair<String, Any?>): JsonObject =
    JsonObject(this + fields.map { (key, value) -> key to toJson(value) })

private fun toJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::toJson))
        else -> error("No JSON for $value")
    }

private fun JsonObject.string(field: String): String = getValue(field).jsonPrimitive.content

private fun JsonObject.stringOrNull(field: String): String? = (this[field] as? JsonPrimitive)?.contentOrNull
