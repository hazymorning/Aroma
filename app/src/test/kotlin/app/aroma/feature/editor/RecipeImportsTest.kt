package app.aroma.feature.editor

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.aroma.data.TestEnvironment
import app.aroma.data.remote.SocialPosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.UUID

/** How an import tells that it ended: on its screen when that is open, otherwise in a notification. */
@RunWith(RobolectricTestRunner::class)
class RecipeImportsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val env = TestEnvironment()
    private val imports =
        RecipeImports(
            context,
            env.recipes,
            RecipeImporter(env.recipes, SocialPosts(OkHttpClient()) { "Test browser" }, untitled = { "Rezept" }),
        )
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ImportNotifications.createChannel(context)
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `an import that ends while nobody looks says how in a notification`() =
        runBlocking {
            val id = UUID.randomUUID()

            // Signed out, the import fails at once, which is all this test needs.
            imports.run(id, ImportRequest(PAGE), System.currentTimeMillis(), rerun = false)

            assertTrue(imports.job(id).value?.outcome is ImportOutcome.Failed)
            assertEquals("only the notification of the ended import is left", 1, notifications.size)
        }

    @Test
    fun `an import whose screen is open ends there, without a notification`() =
        runBlocking {
            val id = UUID.randomUUID()
            val screen = launch(Dispatchers.Unconfined) { imports.job(id).collect { } }

            imports.run(id, ImportRequest(PAGE), System.currentTimeMillis(), rerun = false)
            screen.cancel()

            assertTrue(imports.job(id).value?.outcome is ImportOutcome.Failed)
            assertTrue(notifications.isEmpty())
        }

    private companion object {
        const val PAGE = "https://www.example.com/gnocchi"
    }
}
