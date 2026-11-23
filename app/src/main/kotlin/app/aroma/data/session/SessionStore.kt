package app.aroma.data.session

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.aroma.data.remote.MealieJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    val photoKey: String? = null,
) {
    /** The user's photo in Mealie; users without one get an error and keep their initial. */
    val photoUrl: String? get() = photoKey?.let { "$serverUrl/api/media/users/$userId/profile.webp?cacheKey=$it" }
}

/**
 * Keeps the session encrypted at rest; the token is never written in plain text. The Keystore
 * answers through another process and can take a while, so the cipher is used off the main thread.
 */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: SecretCipher) {
    private val key = stringPreferencesKey("session")

    suspend fun load(): Session? {
        val stored = dataStore.data.first()[key] ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
                MealieJson.decodeFromString(Session.serializer(), plain.decodeToString())
            }.getOrNull()
        }
    }

    suspend fun save(session: Session) {
        val plain = MealieJson.encodeToString(Session.serializer(), session).encodeToByteArray()
        val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(plain) }
        dataStore.edit { it[key] = Base64.encodeToString(encrypted, Base64.NO_WRAP) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
