package app.aroma.testing

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.aroma.AppContainer
import app.aroma.data.session.SessionState
import app.aroma.feature.cook.CookViewModel
import app.aroma.feature.home.HomeViewModel
import app.aroma.feature.login.LoginStep
import app.aroma.feature.login.LoginViewModel
import app.aroma.feature.mealplan.MealPlanViewModel
import app.aroma.feature.recipe.RecipeViewModel
import app.aroma.feature.recipes.RecipesViewModel
import app.aroma.feature.shopping.ShoppingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowPausedLooper
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The app as it runs, signed in to a [FakeMealie] through the login screen: the real [AppContainer]
 * of [TestApp], with its repositories, database and connection, and view models made as the
 * screens make them. The main thread, where view models work, runs only when the test lets it, with
 * [until] or [advanceBy], in virtual time; the network and the database work on threads of their
 * own, as on a phone. A test fails on any exception a coroutine leaves uncaught and on any request
 * the fake could not make sense of.
 */
class ScreenTest : TestRule {
    val mealie = FakeMealie()

    /** Now, for the screens that ask: Monday, 23 November 2026, at half past six in the evening. */
    val clock: Clock = Clock.fixed(MONDAY_EVENING.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())

    val app: TestApp get() = ApplicationProvider.getApplicationContext()
    val container: AppContainer get() = app.container

    /** Whether no request is under way: every answer has come, or was given up. */
    val networkIdle: Boolean get() = calls.runningCallsCount() == 0

    private val calls get() = container.clients.httpClient.dispatcher

    private val looper = Shadow.extract<ShadowPausedLooper>(Looper.getMainLooper())
    private val viewModels = ViewModelStore()
    private val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var opened = 0

    override fun apply(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                UncaughtExceptions.caught.clear()
                WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
                val failures = mutableListOf<Throwable>()
                try {
                    signIn()
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures += failure
                } finally {
                    listening.cancel()
                    viewModels.clear()
                    container.scope.cancel()
                    mealie.close()
                }
                failures += UncaughtExceptions.caught.map { AssertionError("A coroutine left this uncaught", it) }
                failures += mealie.problems.map { AssertionError("The fake Mealie could not answer $it") }
                // Closed last: work just called off may still be on its way out of the database.
                app.database.close()
                MultipleFailureException.assertEmpty(failures)
            }
        }

    /** Signs in as a person does: Mealie's address first, then the email and password. */
    private fun signIn() {
        // The app reads the session kept from its last run as it starts; signing in meanwhile would race with that.
        until { container.sessions.state.value != SessionState.Loading }
        val login = open { LoginViewModel(auth) }
        login.onServerChange(mealie.url)
        login.connect()
        until { login.state.value.step == LoginStep.Credentials }
        login.onUsernameChange(mealie.email)
        login.onPasswordChange(FakeMealie.PASSWORD)
        login.signIn()
        until { container.sessions.current != null && !login.state.value.isWorking }
    }

    fun home() = open { HomeViewModel(recipes, mealPlans, shopping, settings, sessions, auth, ratingReminder, connection, clock) }

    fun recipes() = open { RecipesViewModel(recipes, connection, withCookbooks = true) }

    fun recipe(slug: String) =
        open { RecipeViewModel(slug, recipes, reworks, shopping, mealPlans, sessions, settings, connection, SavedStateHandle()) }
            .also { listen(it.rework) }

    fun cook(slug: String) =
        open { CookViewModel(slug, 1.0, recipes, timeline, timers, settings, ratingReminder, sessions.current?.userId, SavedStateHandle()) }
            .also { listen(it.state) }

    fun plan() = open { MealPlanViewModel(mealPlans, recipes, shopping, settings, connection, clock) }

    fun shopping() = open { ShoppingViewModel(shopping, settings, connection, mealPlans, clock) }

    /** A view model as its screen makes it; cleared when the test ends, as when its screen goes. */
    private fun <VM : ViewModel> open(create: AppContainer.() -> VM): VM {
        val model = container.create()
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = model as T
            }
        ViewModelProvider.create(viewModels, factory)["screen-${++opened}", ViewModel::class]
        return model
    }

    /** Keeps [flow] collected, as a screen showing it does. */
    private fun listen(flow: Flow<*>) {
        listening.launch { flow.collect {} }
    }

    /** What [flow] sends from now on, as a screen that starts listening now sees it. */
    fun <T> collect(flow: Flow<T>): List<T> {
        val seen = CopyOnWriteArrayList<T>()
        listening.launch { flow.collect { seen += it } }
        return seen
    }

    /**
     * Lets the main thread run what is due, again each time the network or the database hands it
     * something, until [done]. Only work on other threads takes real time. Delays on the main
     * thread wait for [advanceBy], or, with [clockRunning], pass as the wait goes on, for what
     * waits out a delay after an answer comes, such as a card that goes a moment after it was rated.
     */
    fun until(clockRunning: Boolean = false, done: () -> Boolean) {
        val deadline = System.nanoTime() + TIMEOUT_MILLIS * NANOS_PER_MILLI
        while (true) {
            if (clockRunning) looper.idleFor(Duration.ofMillis(CLOCK_STEP_MILLIS)) else looper.idle()
            if (done()) return
            val left = (deadline - System.nanoTime()) / NANOS_PER_MILLI
            if (left <= 0) throw AssertionError("Gave up after $TIMEOUT_MILLIS ms; Mealie was last asked ${mealie.requests.takeLast(LAST)}")
            // Wakes as soon as another thread hands the main thread work; what does not, such as a
            // request reaching the fake, is looked at again a moment later.
            looper.poll(minOf(left, POLL_MILLIS))
        }
    }

    /** [until] the screen's [state] is as [done] wants it; returns it. */
    fun <T> awaitState(state: StateFlow<T>, done: (T) -> Boolean): T {
        until { done(state.value) }
        return state.value
    }

    /** The phone loses its network: the app knows at once, and Mealie would not hear from it anyway. */
    fun loseNetwork() {
        mealie.silent = true
        app.network.value = false
        until { !container.connection.isReachable.value }
    }

    /** The phone has a network again, through which Mealie answers. */
    fun regainNetwork() {
        mealie.silent = false
        app.network.value = true
    }

    /** What [flow] holds now, as a screen opened now would find it. */
    fun <T> now(flow: Flow<T>): T = runBlocking { withTimeout(TIMEOUT_MILLIS) { flow.first() } }

    /** Lets [millis] pass on the main thread's clock, running what falls due. */
    fun advanceBy(millis: Long) = looper.idleFor(Duration.ofMillis(millis))

    private companion object {
        val MONDAY_EVENING: LocalDateTime = LocalDateTime.of(2026, 11, 23, 18, 30)
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 5L
        const val CLOCK_STEP_MILLIS = 50L
        const val NANOS_PER_MILLI = 1_000_000L
        const val LAST = 5
    }
}
