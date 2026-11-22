package app.aroma.data.session

import app.aroma.data.AromaError
import app.aroma.data.remote.Credentials
import app.aroma.data.remote.MealieApi
import app.aroma.data.remote.MealieClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val session: Session) : SessionState
}

/** Single source of truth for who is signed in and which server to talk to. */
class SessionManager(private val store: SessionStore) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    lateinit var clientFactory: MealieClientFactory

    @Volatile
    private var cachedApi: Pair<String, MealieApi>? = null

    val current: Session? get() = (state.value as? SessionState.SignedIn)?.session

    suspend fun restore() {
        mutableState.value = store.load()?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut
    }

    suspend fun start(session: Session) {
        store.save(session)
        mutableState.value = SessionState.SignedIn(session)
    }

    suspend fun end() {
        store.clear()
        cachedApi = null
        mutableState.value = SessionState.SignedOut
    }

    fun credentials(): Credentials? = current?.let { Credentials(baseUrl(it.serverUrl), it.token) }

    /** API bound to the signed-in server; throws [AromaError.Unauthorized] when nobody is signed in. */
    fun api(): MealieApi {
        val session = current ?: throw AromaError.Unauthorized()
        cachedApi?.let { (url, api) -> if (url == session.serverUrl) return api }
        return clientFactory.create(baseUrl(session.serverUrl)).also { cachedApi = session.serverUrl to it }
    }

    fun requireServerUrl(): String = current?.serverUrl ?: throw AromaError.Unauthorized()

    companion object {
        fun baseUrl(serverUrl: String): HttpUrl = "$serverUrl/".toHttpUrl()
    }
}
