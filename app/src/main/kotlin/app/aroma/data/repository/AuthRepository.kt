package app.aroma.data.repository

import app.aroma.core.model.ServerInfo
import app.aroma.data.AromaError
import app.aroma.data.local.AromaDatabase
import app.aroma.data.mealieCall
import app.aroma.data.remote.MealieClientFactory
import app.aroma.data.remote.ServerAddress
import app.aroma.data.remote.dto.ApiTokenRequest
import app.aroma.data.remote.dto.UserDto
import app.aroma.data.session.Revocation
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
    /** Removes what an account leaves on this device besides the database; see [forgetLocal]. */
    private val forgetOutsideDatabase: suspend () -> Unit = {},
) {
    /**
     * Finds the Mealie server behind what the user typed. Without a scheme, https is tried first;
     * plain http only for hosts on a home network (see [ServerAddress]).
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
                firstError = firstError ?: if (failure is AromaError.NotFound) AromaError.NotMealie() else failure
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
        val accessToken = signingIn(401, 400) { api.signIn(username.trim(), password).accessToken }
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
        val user = signingIn(401) { api.currentUser("Bearer ${token.trim()}") }
        start(server, token.trim(), null, user)
    }

    /** Runs [block], a sign-in: Mealie answering with one of the [refusals] means the credentials are wrong. */
    private suspend fun <T> signingIn(vararg refusals: Int, block: suspend () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw if (error is HttpException && error.code() in refusals) AromaError.InvalidCredentials() else error.toAromaError()
        }

    /** Reads the signed-in user again, so a new name set in Mealie shows here too. */
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
                )
            }
        }
    }

    private suspend fun start(server: ServerInfo, token: String, tokenId: Int?, user: UserDto) {
        forgetLocal()
        sessions.start(
            Session(
                serverUrl = server.url,
                token = token,
                tokenId = tokenId,
                userId = user.id,
                username = user.username,
                fullName = user.fullName.orEmpty(),
                email = user.email,
                groupSlug = user.groupSlug,
                serverVersion = server.version,
                allowPasswordLogin = server.allowPasswordLogin,
            ),
        )
    }

    /**
     * Ends the session at once, so the screens leave and no request carries the token any more,
     * forgets everything stored for it, and then revokes the token Aroma created. The token is kept,
     * encrypted, until Mealie answers: when it is out of reach now, or Android ends the app first,
     * the token is revoked at a later start or sign-out.
     */
    suspend fun signOut() {
        val session = sessions.current ?: return
        sessions.end(revoke = session.tokenId?.let { Revocation(session.serverUrl, it, session.token) })
        forgetLocal()
        revokeEndedTokens()
    }

    /**
     * Removes everything an account stored on this device. Besides at sign-out it runs before every
     * sign-in and at a start without a session, so what a sign-out cut short, a session that could
     * not be read any more or an answer that came in late left behind never reaches the next account.
     * Running it again changes nothing.
     */
    suspend fun forgetLocal() {
        forgetOutsideDatabase()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    /**
     * Revokes the tokens of ended sessions, if Mealie answers within a few seconds. A token is
     * forgotten once Mealie took it back or refused to; one it could not be asked about is tried again later.
     */
    suspend fun revokeEndedTokens() {
        for (revocation in sessions.revocations()) {
            val api = clients.create(SessionManager.baseUrl(revocation.serverUrl))
            val answered =
                withTimeoutOrNull(REVOKE_PATIENCE) {
                    try {
                        api.deleteApiToken(revocation.tokenId, "Bearer ${revocation.token}")
                        true
                    } catch (_: IOException) {
                        // Out of reach: many servers answer only at home.
                        false
                    } catch (error: HttpException) {
                        // Mealie refuses a token revoked in its settings already; a 5xx may be a proxy in front of a Mealie that is down.
                        error.code() !in 500..599
                    }
                } ?: false
            if (answered) sessions.revoked(revocation)
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
