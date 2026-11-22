package app.kochi.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialScopeTest {
    private val server = "https://mealie.example.com/".toHttpUrl()

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
        val subPath = "https://example.com/mealie/".toHttpUrl()
        assertTrue("https://example.com/mealie/api/recipes".toHttpUrl().belongsTo(subPath))
        assertFalse("https://example.com/other/api/recipes".toHttpUrl().belongsTo(subPath))
    }
}
