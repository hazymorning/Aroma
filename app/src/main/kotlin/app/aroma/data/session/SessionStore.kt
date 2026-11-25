package app.aroma.data.session

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.aroma.data.remote.MealieJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val serverUrl: String,
    val token: String,
    val tokenId: Int? = null,
    val userId: String,
    val username: String,
    val fullName: String = "",
    val email: String = "",
    val groupSlug: String? = null,
    val serverVersion: String = "",
    val allowPasswordLogin: Boolean = true,
)

/** A token Aroma created for a session that has ended, to be revoked in Mealie. */
@Serializable
data class Revocation(val serverUrl: String, val tokenId: Int, val token: String)

/**
 * Keeps the session, and the tokens of ended sessions still to revoke, encrypted at rest; a token is
 * never written in plain text. The Keystore answers through another process and can take a while,
 * so the cipher is used off the main thread.
 */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: SecretCipher) {
    private val key = stringPreferencesKey("session")

    // Each token is encrypted on its own, so one can be added or taken away without reading the others.
    private val revocationsKey = stringSetPreferencesKey("revocations")

    suspend fun load(): Session? {
        val stored = dataStore.data.first()[key] ?: return null
        return decrypt(stored, Session.serializer())
    }

    suspend fun save(session: Session) {
        val encrypted = encrypt(session, Session.serializer())
        dataStore.edit { it[key] = encrypted }
    }

    /**
     * Forgets the session and keeps [revoke] in the same write, so its token waits in [revocations]
     * even when Android ends the app right after.
     */
    suspend fun clear(revoke: Revocation? = null) {
        val kept = revoke?.let { encrypt(it, Revocation.serializer()) }
        dataStore.edit {
            it.remove(key)
            if (kept != null) it[revocationsKey] = it[revocationsKey].orEmpty() + kept
        }
    }

    /** The tokens [clear] kept that are not [revoked] yet. */
    suspend fun revocations(): List<Revocation> =
        dataStore.data
            .first()[revocationsKey]
            .orEmpty()
            .mapNotNull { decrypt(it, Revocation.serializer()) }

    /** Forgets [revocation], once Mealie took the token back or never will. */
    suspend fun revoked(revocation: Revocation) {
        val stored =
            dataStore.data
                .first()[revocationsKey]
                .orEmpty()
                .filter { decrypt(it, Revocation.serializer()) == revocation }
        dataStore.edit { it[revocationsKey] = it[revocationsKey].orEmpty() - stored }
    }

    private suspend fun <T> encrypt(value: T, serializer: KSerializer<T>): String {
        val plain = MealieJson.encodeToString(serializer, value).encodeToByteArray()
        val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(plain) }
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private suspend fun <T> decrypt(stored: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            runCatching {
                val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
                MealieJson.decodeFromString(serializer, plain.decodeToString())
            }.getOrNull()
        }
}
