package app.aroma.feature.settings

import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.data.session.Revocation
import app.aroma.testing.ScreenTest
import app.aroma.testing.TestApp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class SignOutScreenTest {
    @get:Rule
    val screen = ScreenTest()

    private val mealie get() = screen.mealie
    private val container get() = screen.container

    @Test
    fun `signing out while Mealie is out of reach, every tab open, crashes nothing and leaves nothing behind`() {
        val soup = mealie.addRecipe("Linsensuppe", totalTime = "PT20M", categories = listOf("Abendessen"))
        mealie.plan(MONDAY, soup)
        val list = mealie.addList("Wocheneinkauf", "Milch")
        val home = screen.home()
        val recipes = screen.recipes()
        val plan = screen.plan()
        val shopping = screen.shopping()
        screen.awaitState(home.state) { it.recent.isNotEmpty() }
        screen.awaitState(recipes.state) { it.recipes.isNotEmpty() }
        screen.awaitState(plan.state) { it.weekItems != null }
        screen.awaitState(shopping.state) { it.list != null }

        // Mealie stops answering, and the next item added waits for it once its tries are over.
        mealie.refusing = true
        shopping.onNewItemChange("Eier")
        shopping.addItem()
        screen.awaitState(shopping.state) { it.isOffline && it.pendingChanges == 1 && screen.networkIdle }
        val session = container.sessions.current!!
        val signedOutAt = mealie.requests.size

        val signOut = container.signOut()
        screen.until { signOut.isCompleted }
        // The tabs still open load anew as the connection starts over; then Mealie answers again, the
        // phone rejoins its network and Android runs the sync it was asked for.
        mealie.refusing = false
        screen.loseNetwork()
        screen.regainNetwork()
        screen.until { container.connection.isReachable.value && screen.networkIdle }
        runWaitingSync()

        val withOldToken = mealie.requests.drop(signedOutAt).filter { it.token == session.token }
        assertEquals(setOf("DELETE /api/users/api-tokens/${session.tokenId}"), withOldToken.map { "${it.method} ${it.path}" }.toSet())
        assertEquals(listOf(Revocation(mealie.url, session.tokenId!!, session.token)), runBlocking { container.sessions.revocations() })
        assertEquals(0, screen.now(container.shopping.pendingCount(list)))
        assertNull(screen.now(container.shopping.observe(list)))
        assertNull(screen.now(container.shopping.cachedLists()))
        assertEquals(0, screen.now(container.recipes.offlineCount()))
        assertNull(screen.now(container.settings.settings).lastShoppingList)
        assertEquals(listOf("Milch"), mealie.items(list).map { it.getValue("note").jsonPrimitive.content })
    }

    /** Runs the shopping sync Android was asked to run once the phone has a network. */
    private fun runWaitingSync() {
        val work = WorkManager.getInstance(screen.app)
        val queued = work.getWorkInfosForUniqueWork("shopping-sync").get()
        val waiting = queued.filterNot { it.state.isFinished }.map { it.id }
        waiting.forEach { WorkManagerTestInitHelper.getTestDriver(screen.app)!!.setAllConstraintsMet(it) }
        screen.until { waiting.all { id -> work.isFinished(id) } }
    }

    private fun WorkManager.isFinished(id: UUID): Boolean {
        val info = getWorkInfoById(id).get()
        return info?.state?.isFinished == true
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)
    }
}
