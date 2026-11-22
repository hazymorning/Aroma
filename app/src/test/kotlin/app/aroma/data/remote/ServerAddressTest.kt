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
    fun `public addresses never fall back to plain http unless it is typed`() {
        assertEquals(listOf("https://mealie.example.com/"), candidates("mealie.example.com"))
        assertEquals(listOf("https://203.0.113.7:9000/"), candidates("203.0.113.7:9000"))
        assertEquals(listOf("http://mealie.example.com/"), candidates("http://mealie.example.com"))
    }

    @Test
    fun `home and tunnel addresses may use plain http`() {
        listOf(
            "mealie",
            "mealie.local",
            "mealie.fritz.box",
            "10.0.0.2",
            "172.20.1.1",
            "192.168.178.20",
            "100.101.102.103",
            "kitchen.tail1234.ts.net",
        ).forEach { host -> assertTrue(host, ServerAddress.isPrivate(host)) }
        listOf("mealie.example.com", "8.8.8.8", "172.32.0.1", "100.128.0.1")
            .forEach { host -> assertTrue(host, !ServerAddress.isPrivate(host)) }
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
