package app.aroma.data.session

import app.aroma.data.remote.MealieJson
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTest {
    @Test
    fun `a session saved by an earlier version still loads`() {
        val saved =
            """{"serverUrl":"https://mealie.example","token":"secret","userId":"u1","username":"mara",""" +
                """"fullName":"Mara","photoKey":"2026-11-20T10:00:00"}"""
        val session = MealieJson.decodeFromString(Session.serializer(), saved)
        assertEquals("u1", session.userId)
        assertEquals("Mara", session.fullName)
    }
}
