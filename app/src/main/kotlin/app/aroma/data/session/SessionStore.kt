package app.aroma.data.session

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.aroma.data.remote.MealieJson
import kotlinx.coroutines.flow.first
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
    val isAdmin: Boolean = false,
    val groupSlug: String? = null,
    val serverVersion: String = "",
    val allowPasswordLogin: Boolean = true,
)

/** Keeps the session encrypted at rest; the token is never written in plain text. */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: SecretCipher) {
    private val key = stringPreferencesKey("session")

    suspend fun load(): Session? {
        val stored = dataStore.data.first()[key] ?: return null
        return runCatching {
            val plain = cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP))
            MealieJson.decodeFromString(Session.serializer(), plain.decodeToString())
        }.getOrNull()
    }

    suspend fun save(session: Session) {
        val encrypted = cipher.encrypt(MealieJson.encodeToString(Session.serializer(), session).encodeToByteArray())
        dataStore.edit { it[key] = Base64.encodeToString(encrypted, Base64.NO_WRAP) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
