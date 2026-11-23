package app.aroma.data.session

import app.aroma.data.AromaError
import app.aroma.data.remote.Credentials
import app.aroma.data.remote.MealieApi
import app.aroma.data.remote.MealieClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val session: Session) : SessionState
}

/** Single source of truth for who is signed in and which server to talk to. */
class SessionManager(private val store: SessionStore, private val clientFactory: MealieClientFactory) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    @Volatile
    private var cachedApi: Pair<String, MealieApi>? = null

    val current: Session? get() = (state.value as? SessionState.SignedIn)?.session

    suspend fun restore() {
        mutableState.value = store.load()?.let { SessionState.SignedIn(it) } ?: SessionState.SignedOut
    }

    // Signing in, refreshing the user and signing out never interleave, so a refresh cannot bring
    // back a session that just ended.
    private val changes = Mutex()

    suspend fun start(session: Session) =
        changes.withLock {
            store.save(session)
            mutableState.value = SessionState.SignedIn(session)
        }

    /** Keeps newer details of the signed-in user; does nothing once that user signed out. */
    suspend fun update(change: (Session) -> Session) =
        changes.withLock {
            val session = current ?: return@withLock
            val changed = change(session)
            if (changed == session) return@withLock
            store.save(changed)
            mutableState.value = SessionState.SignedIn(changed)
        }

    suspend fun end() =
        changes.withLock {
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
