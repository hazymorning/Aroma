package app.aroma.data

import app.aroma.data.local.CacheDao
import app.aroma.data.local.CacheEntity
import app.aroma.data.network.ServerConnection
import app.aroma.data.remote.MealieJson
import app.aroma.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/** Stores server responses as JSON so screens have something to show without a connection. */
class JsonCache(private val dao: CacheDao, private val connection: ServerConnection, private val sessions: SessionManager) {
    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? =
        dao.get(key)?.let { entry ->
            runCatching { MealieJson.decodeFromString(serializer, entry.json) }.getOrNull()
        }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        dao.put(CacheEntity(key, MealieJson.encodeToString(serializer, value), System.currentTimeMillis()))
    }

    /** The copy under [key], again after every write to it; null while there is none that can be read. */
    fun <T> observe(key: String, serializer: KSerializer<T>): Flow<T?> =
        dao
            .observe(key)
            .distinctUntilChanged()
            .map { entry -> entry?.let { runCatching { MealieJson.decodeFromString(serializer, it.json) }.getOrNull() } }
            .flowOn(Dispatchers.Default)

    /**
     * Emits the cached copy first (if any), then the fresh one. Fails only when there is nothing
     * to show. While Mealie is out of reach, the cached copy is all there is, at once and marked
     * offline. Decoding runs off the main thread, so screens stay smooth while lists arrive. An
     * answer that arrives after its sign-in ended is not kept, so the next account never sees it.
     */
    fun <T> stream(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): Flow<Loadable<T>> =
        flow<Loadable<T>> {
            val cached = read(key, serializer)
            if (cached != null && connection.isReachable.value) emit(Loadable(cached, isRefreshing = true))
            val signIn = sessions.current?.token
            val fresh =
                try {
                    connection.whileReachable { fetch() }
                } catch (error: Throwable) {
                    val failure = error.toAromaError()
                    if (cached == null) throw failure
                    emit(Loadable(cached, error = failure))
                    return@flow
                }
            if (sessions.current?.token == signIn) write(key, serializer, fresh)
            emit(Loadable(fresh))
        }.flowOn(Dispatchers.Default)
}
