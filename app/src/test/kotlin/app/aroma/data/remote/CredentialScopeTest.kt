package app.aroma.data.remote

import app.aroma.data.session.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialScopeTest {
    /** The server as the app holds it: stored without a trailing slash, as [ServerAddress.display] writes it. */
    private val server = SessionManager.baseUrl("https://mealie.example.com")

    @Test
    fun `the token is sent to its own server`() {
        assertTrue("https://mealie.example.com/api/recipes".toHttpUrl().belongsTo(server))
        assertTrue("https://mealie.example.com/api/media/recipes/1/images/original.webp".toHttpUrl().belongsTo(server))
    }

    @Test
    fun `the token never goes to another host, port or scheme`() {
        assertFalse("https://images.example.com/api/recipes".toHttpUrl().belongsTo(server))
        assertFalse("https://mealie.example.com:8443/api/recipes".toHttpUrl().belongsTo(server))
        assertFalse("http://mealie.example.com/api/recipes".toHttpUrl().belongsTo(server))
    }

    @Test
    fun `servers below a sub path only match that path`() {
        val subPath = SessionManager.baseUrl("https://example.com/mealie")
        assertTrue("https://example.com/mealie/api/recipes".toHttpUrl().belongsTo(subPath))
        assertFalse("https://example.com/other/api/recipes".toHttpUrl().belongsTo(subPath))
        assertFalse("https://example.com/mealie2/api/recipes".toHttpUrl().belongsTo(subPath))
    }
}
