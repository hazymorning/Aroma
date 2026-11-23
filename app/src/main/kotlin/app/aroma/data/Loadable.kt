package app.aroma.data

import app.aroma.data.local.CacheDao
import app.aroma.data.local.CacheEntity
import app.aroma.data.remote.MealieJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/** Data plus how fresh it is: [error] is set when the newest copy could not be fetched. */
data class Loadable<T>(val data: T, val isRefreshing: Boolean = false, val error: AromaError? = null) {
    val isOffline: Boolean get() = error?.isConnectivity == true
}

/** Stores server responses as JSON so screens have something to show without a connection. */
class JsonCache(private val dao: CacheDao) {
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
     * to show. Decoding runs off the main thread, so screens stay smooth while lists arrive.
     */
    fun <T> stream(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): Flow<Loadable<T>> =
        flow<Loadable<T>> {
            val cached = read(key, serializer)
            if (cached != null) emit(Loadable(cached, isRefreshing = true))
            val fresh =
                try {
                    fetch()
                } catch (error: Throwable) {
                    val failure = error.toAromaError()
                    if (cached == null) throw failure
                    emit(Loadable(cached, error = failure))
                    return@flow
                }
            write(key, serializer, fresh)
            emit(Loadable(fresh))
        }.flowOn(Dispatchers.Default)
}
