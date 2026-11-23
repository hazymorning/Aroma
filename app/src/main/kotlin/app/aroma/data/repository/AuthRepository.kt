package app.aroma.data.repository

import app.aroma.core.model.ServerInfo
import app.aroma.data.AromaError
import app.aroma.data.local.AromaDatabase
import app.aroma.data.mealieCall
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.remote.ServerAddress
import app.aroma.data.remote.dto.ApiTokenRequest
import app.aroma.data.remote.dto.UserDto
import app.aroma.data.session.Session
import app.aroma.data.session.SessionManager
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

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
     * long-lived API token named after this device, so the user stays signed in. The user is read
     * first: a token minted for a sign-in that then fails would stay valid with nobody holding it.
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
        val user = mealieCall { api.currentUser(bearer) }
        val apiToken =
            try {
                mealieCall { api.createApiToken(ApiTokenRequest("Aroma ($deviceName)"), bearer) }
            } catch (_: AromaError) {
                // Servers that refuse API tokens still accept the session token until it expires.
                null
            }
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

    /** Reads the signed-in user again, so a new name or photo set in Mealie shows here too. */
    suspend fun refreshUser() {
        val user = mealieCall { sessions.api().currentUser() }
        sessions.update {
            if (it.userId != user.id) {
                it
            } else {
                it.copy(
                    username = user.username,
                    fullName = user.fullName.orEmpty(),
                    email = user.email,
                    isAdmin = user.admin,
                    photoKey = user.cacheKey,
                )
            }
        }
    }

    private suspend fun start(server: ServerInfo, token: String, tokenId: Int?, user: UserDto) {
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
                photoKey = user.cacheKey,
            ),
        )
    }

    /**
     * Ends the session at once, so the screens leave and no request carries the token any more,
     * forgets everything stored for it, and then revokes the token Aroma created, if Mealie answers
     * within a few seconds.
     */
    suspend fun signOut() {
        val session = sessions.current ?: return
        sessions.end()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        val tokenId = session.tokenId ?: return
        withTimeoutOrNull(REVOKE_PATIENCE) {
            try {
                clients.create(SessionManager.baseUrl(session.serverUrl)).deleteApiToken(tokenId, "Bearer ${session.token}")
            } catch (_: IOException) {
                // Unreachable: the token stays valid on the server, as a token deleted there would anyway.
            } catch (_: HttpException) {
                // Already revoked in Mealie's settings.
            }
        }
    }
}

private val REVOKE_PATIENCE = 5.seconds

object MealieVersion {
    /** Mealie 2.0 introduced households, which Aroma relies on. Development builds are accepted. */
    fun isSupported(version: String): Boolean {
        val major = version.removePrefix("v").substringBefore('.').toIntOrNull() ?: return true
        return major >= 2
    }

    /** Whether [version] is [major].[minor] or newer; development builds count as newest. */
    fun isAtLeast(version: String, major: Int, minor: Int): Boolean {
        val parts = version.removePrefix("v").split('.').map { it.toIntOrNull() }
        val versionMajor = parts.getOrNull(0) ?: return true
        val versionMinor = parts.getOrNull(1) ?: 0
        return versionMajor > major || (versionMajor == major && versionMinor >= minor)
    }
}
