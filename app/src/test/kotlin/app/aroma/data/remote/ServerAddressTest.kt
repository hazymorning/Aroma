package app.aroma.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    private fun candidates(input: String) = ServerAddress.candidates(input).map { it.toString() }

    @Test
    fun `without a scheme https is tried before http`() {
        assertEquals(listOf("https://mealie.home/", "http://mealie.home/"), candidates("mealie.home"))
    }

    @Test
    fun `ports, sub paths and trailing slashes are kept clean`() {
        assertEquals(listOf("https://192.168.1.5:9000/", "http://192.168.1.5:9000/"), candidates(" 192.168.1.5:9000/ "))
        assertEquals(listOf("https://example.com/mealie/"), candidates("https://example.com/mealie/"))
    }

    @Test
    fun `addresses copied from the browser are accepted`() {
        assertEquals(listOf("https://mealie.example.com/"), candidates("https://mealie.example.com/g/home"))
        assertEquals(listOf("http://mealie.local:9000/"), candidates("http://mealie.local:9000/login"))
    }

    @Test
    fun `nonsense yields nothing to try`() {
        assertTrue(candidates("").isEmpty())
        assertTrue(candidates("not a url").isEmpty())
    }
}
