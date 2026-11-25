package app.aroma.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The version a server reports decides whether Aroma signs in and which imports it offers. */
class MealieVersionTest {
    @Test
    fun `imports from texts and photos are offered from Mealie 3_23 on, and by development builds`() {
        listOf("v3.23.0", "3.23.1", "v3.31.0", "v4.0.0", "nightly").forEach { version ->
            assertTrue(version, MealieVersion.isAtLeast(version, 3, 23))
        }
        listOf("v3.22.9", "3.9.0", "v2.8.0").forEach { version ->
            assertFalse(version, MealieVersion.isAtLeast(version, 3, 23))
        }
    }

    @Test
    fun `Mealie 2 and newer are supported, as are development builds`() {
        listOf("v2.0.0", "2.8.0", "v3.31.0", "nightly").forEach { version -> assertTrue(version, MealieVersion.isSupported(version)) }
        listOf("v1.12.0", "0.5.6").forEach { version -> assertFalse(version, MealieVersion.isSupported(version)) }
    }
}
