package app.aroma.data.repository

import app.aroma.core.model.ServerInfo
import app.aroma.data.AromaError
import app.aroma.data.local.AromaDatabase
import app.aroma.data.mealieCall
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.remote.ServerAddress
import app.aroma.data.remote.dto.ApiTokenRequest
import app.aroma.data.session.Session
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class AuthRepository(
    private val clients: MealieClientFactory,
    private val sessions: SessionManager,
    private val database: AromaDatabase,
    private val deviceName: String,
) {
    /**
     * Finds the Mealie server behind what the user typed. Without a scheme, https is tried
     * first and plain http second, which covers most home setups.
     */
    suspend fun probe(input: String): ServerInfo {
        val candidates = ServerAddress.candidates(input)
        if (candidates.isEmpty()) throw AromaError.HostNotFound()
        var firstError: AromaError? = null
        for (url in candidates) {
            try {
                val about = clients.create(url).about()
                if (about.version.isBlank()) throw AromaError.NotMealie()
                if (!MealieVersion.isSupported(about.version)) throw AromaError.UnsupportedVersion(about.version)
                return ServerInfo(ServerAddress.display(url), about.version.removePrefix("v"), about.allowPasswordLogin)
            } catch (error: Throwable) {
                val failure = error.toAromaError()
                if (failure is AromaError.UnsupportedVersion) throw failure
                if (failure is AromaError.NotFound) firstError = firstError ?: AromaError.NotMealie()
                firstError = firstError ?: failure
            }
        }
        throw firstError ?: AromaError.HostNotFound()
    }

    /**
     * Signs in with username and password, then trades the short-lived session token for a
     * long-lived API token named after this device, so the user stays signed in.
     */
    suspend fun signIn(server: ServerInfo, username: String, password: String) {
        val api = clients.create(SessionManager.baseUrl(server.url))
        val accessToken =
            try {
                api.signIn(username.trim(), password).accessToken
            } catch (error: HttpException) {
                throw if (error.code() == 401 || error.code() == 400) AromaError.InvalidCredentials() else error.toAromaError()
            } catch (error: Throwable) {
                throw error.toAromaError()
            }
        val bearer = "Bearer $accessToken"
        val apiToken = runCatching { api.createApiToken(ApiTokenRequest("Aroma ($deviceName)"), bearer) }.getOrNull()
        val user = mealieCall { api.currentUser(bearer) }
        start(server, apiToken?.token ?: accessToken, apiToken?.id, user)
    }

    suspend fun signInWithToken(server: ServerInfo, token: String) {
        val api = clients.create(SessionManager.baseUrl(server.url))
        val user =
            try {
                api.currentUser("Bearer ${token.trim()}")
            } catch (error: HttpException) {
                throw if (error.code() == 401) AromaError.InvalidCredentials() else error.toAromaError()
            } catch (error: Throwable) {
                throw error.toAromaError()
            }
        start(server, token.trim(), null, user)
    }

    private suspend fun start(server: ServerInfo, token: String, tokenId: Int?, user: app.aroma.data.remote.dto.UserDto) {
        sessions.start(
            Session(
                serverUrl = server.url,
                token = token,
                tokenId = tokenId,
                userId = user.id,
                username = user.username,
                fullName = user.fullName.orEmpty(),
                email = user.email,
                isAdmin = user.admin,
                groupSlug = user.groupSlug,
                serverVersion = server.version,
                allowPasswordLogin = server.allowPasswordLogin,
            ),
        )
    }

    /** Revokes the token Aroma created (if reachable) and forgets everything stored locally. */
    suspend fun signOut() {
        val tokenId = sessions.current?.tokenId
        if (tokenId != null) runCatching { sessions.api().deleteApiToken(tokenId) }
        withContext(Dispatchers.IO) { database.clearAllTables() }
        sessions.end()
    }
}

object MealieVersion {
    /** Mealie 2.0 introduced households, which Aroma relies on. Development builds are accepted. */
    fun isSupported(version: String): Boolean {
        val major = version.removePrefix("v").substringBefore('.').toIntOrNull() ?: return true
        return major >= 2
    }
}
