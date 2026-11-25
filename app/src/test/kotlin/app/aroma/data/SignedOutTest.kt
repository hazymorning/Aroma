package app.aroma.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * A screen still open as its sign-in ends may ask for its lists once more, as when Mealie answers
 * again. It hears that nobody is signed in where it listens for failures, and asking does not throw.
 */
@RunWith(RobolectricTestRunner::class)
class SignedOutTest {
    private val env = TestEnvironment()

    @After
    fun tearDown() = env.close()

    @Test
    fun `lists asked for while signed out fail once collected, not when asked for`() =
        runBlocking {
            val day = LocalDate.of(2026, 11, 23)
            val lists: List<Flow<*>> =
                listOf(
                    env.mealPlans.range(day, 7),
                    env.mealPlans.upcoming(),
                    env.recipes.quick(day),
                    env.recipes.recent(),
                    env.recipes.suggestions(emptyList(), day),
                    env.recipes.cookbooks(),
                )

            lists.forEach { list ->
                val failure = runCatching { list.first() }.exceptionOrNull()
                assertTrue("$failure", failure is AromaError.Unauthorized)
            }
        }
}
