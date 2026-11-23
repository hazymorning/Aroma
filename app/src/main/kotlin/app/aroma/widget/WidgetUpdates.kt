package app.aroma.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.aroma.AppContainer
import app.aroma.BuildConfig
import app.aroma.data.attempt
import app.aroma.data.session.SessionState
import app.aroma.data.settings.inAppLanguage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the widgets current. They draw what Room holds; this keeps Room and the widgets in step.
 * Changes made in the app or in a widget draw them again at once, and while any widget is on a
 * home screen a periodic refresh brings in what changed on the server.
 */
internal object Widgets {
    private const val REFRESH = "widget-refresh"
    private val REFRESH_INTERVAL = Duration.ofMinutes(30)

    /** Changes that come in a burst, like items checked one after another, draw the widgets once. */
    private val SETTLE = 1.seconds

    /** Draws every widget of both kinds again. */
    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        ShoppingWidget().updateAll(context)
    }

    /**
     * For as long as the app runs, draws the widgets again after a change to the plan or a list made
     * here, by the app or a widget, and after a change of account or of the app's look. All of it is
     * done on a best effort: what fails waits for the next change or refresh and never reaches the app.
     */
    @OptIn(FlowPreview::class)
    fun follow(context: Context, container: AppContainer) {
        container.scope.launch {
            val account =
                container.sessions.state
                    .filter { it != SessionState.Loading }
                    .map { (it as? SessionState.SignedIn)?.session?.let { session -> session.serverUrl to session.userId } }
                    .distinctUntilChanged()
                    .drop(1)
            val look = container.widgetLooks.onEach { attempt { publishPreviews(context, it) } }.drop(1)
            val appList =
                container.settings.settings
                    .map { it.lastShoppingList }
                    .distinctUntilChanged()
                    .drop(1)
            // Planning goes straight to the server, so the widgets' copy of the week is fetched again first.
            val plan = container.mealPlans.changes.onEach { refreshPlan(context, container) }
            merge(account, look, appList, container.shopping.changes, plan)
                .debounce(SETTLE)
                .collect { attempt { updateAll(context) } }
        }
    }

    /** Keeps the periodic refresh scheduled while a widget is on a home screen; scheduling it again changes nothing. */
    fun keepFresh(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REFRESH, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The last widget of a kind is gone; once neither kind is left, so is the refresh. */
    fun removed(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val receivers = listOf(TodayWidgetReceiver::class.java, ShoppingWidgetReceiver::class.java)
        if (receivers.all { manager.getAppWidgetIds(ComponentName(context, it)).isEmpty() }) {
            WorkManager.getInstance(context).cancelUniqueWork(REFRESH)
        }
    }

    /** Fetches what the widgets show: the week ahead for Today widgets, and the lists shopping widgets show. */
    suspend fun refresh(context: Context, container: AppContainer) {
        refreshPlan(context, container)
        shownShoppingLists(context, container).forEach { attempt { container.shopping.refresh(it) } }
    }

    private suspend fun refreshPlan(context: Context, container: AppContainer) {
        attempt {
            if (GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).isNotEmpty()) {
                container.mealPlans.refreshUpcoming(LocalDate.now())
            }
        }
    }

    /**
     * From Android 15 on, the launcher's picker shows the widgets as the app draws them: in its
     * language and look. They are drawn again when either changes or the app is updated, as often
     * as the system allows.
     */
    private suspend fun publishPreviews(context: Context, look: WidgetLook) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val drawn = "${BuildConfig.VERSION_CODE} $look ${context.inAppLanguage().resources.configuration.locales.toLanguageTags()}"
        val record = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        if (record.getString(PREVIEWS, null) == drawn) return
        val manager = GlanceAppWidgetManager(context)
        val results = listOf(TodayWidgetReceiver::class, ShoppingWidgetReceiver::class).map { manager.setWidgetPreviews(it) }
        if (results.all { it == SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) record.edit { putString(PREVIEWS, drawn) }
    }

    private const val PREVIEWS = "previews"
}

/** Brings in from the server what the widgets show, then draws them again. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (container.sessions.state.first { it != SessionState.Loading } is SessionState.SignedIn) {
            Widgets.refresh(applicationContext, container)
        }
        Widgets.updateAll(applicationContext)
        return Result.success()
    }
}
