package app.aroma.feature.editor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aroma.AromaApplication
import app.aroma.data.AromaError
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.session.SessionState
import app.aroma.data.toAromaError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An import as its screen and its notification show it. [startedAt] is wall-clock time in milliseconds. */
data class ImportJob(
    val request: ImportRequest,
    val startedAt: Long,
    val steps: List<ImportStep> = emptyList(),
    val outcome: ImportOutcome? = null,
) {
    /** This import at [step]; a step reported again stays one line. */
    fun next(step: ImportStep): ImportJob = if (steps.lastOrNull() == step) this else copy(steps = steps + step)
}

sealed interface ImportOutcome {
    data class Added(val slug: String, val name: String) : ImportOutcome

    data class Failed(val error: AromaError) : ImportOutcome
}

/**
 * Runs imports as background work, one after another. An import goes on when its screen is left,
 * with a notification while it runs, and tells how it ended in another notification unless its
 * screen is open to show it. Screens follow an import by its id.
 */
class RecipeImports(private val context: Context, private val recipes: RecipeRepository, private val importer: RecipeImporter) {
    private val jobs = ConcurrentHashMap<UUID, MutableStateFlow<ImportJob?>>()

    private fun state(id: UUID) = jobs.getOrPut(id) { MutableStateFlow(null) }

    /** Import [id] as it goes, collected by its screen while the screen is shown; null once the app was ended. */
    fun job(id: UUID): StateFlow<ImportJob?> = state(id)

    /** Starts an import. Fails at once, with nothing sent, when Mealie cannot be reached. */
    suspend fun start(request: ImportRequest): UUID {
        val known = recipes.newestRecipeIds()
        val startedAt = System.currentTimeMillis()
        val work =
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(ImportWorker.input(request, known, startedAt))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        state(work.id).value = ImportJob(request, startedAt)
        // Imports queue behind each other, so two never wait for a new recipe at the same time.
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        return work.id
    }

    /** The screen has shown how import [id] ended; its notification is no longer needed. */
    fun shown(id: UUID) = ImportNotifications.dismiss(context, id)

    /**
     * Runs import [id] for its worker. [saved] is the request as the worker keeps it, without a
     * pasted text, which may be too long for it: the text is taken from the running app.
     */
    internal suspend fun run(
        id: UUID,
        saved: ImportRequest,
        known: Set<String>,
        startedAt: Long,
        resumed: Boolean,
    ) {
        val state = state(id)
        val request = state.value?.request ?: saved
        state.value = ImportJob(request, startedAt)
        // A screen collects its import only while it is shown: nobody collecting means nobody sees it.
        val seen = { state.subscriptionCount.value > 0 }
        val onStep = { step: ImportStep ->
            val job = state.updateAndGet { it?.next(step) }
            if (job != null && !seen()) ImportNotifications.running(context, id, job)
        }
        val outcome =
            try {
                val slug = if (resumed) importer.resume(request, known, onStep) else importer.import(request, known, onStep)
                ImportOutcome.Added(slug, nameOf(slug))
            } catch (error: Exception) {
                // Whatever went wrong is told, rather than leaving the import running for good.
                ImportOutcome.Failed(error.toAromaError())
            }
        val job = state.updateAndGet { it?.copy(outcome = outcome) } ?: return
        ImportNotifications.ended(context, id, job, seen())
    }

    private suspend fun nameOf(slug: String): String =
        try {
            (recipes.recipeJson(slug)["name"] as? JsonPrimitive)?.contentOrNull ?: slug
        } catch (_: AromaError) {
            slug
        }

    private companion object {
        const val QUEUE = "recipe-import"
    }
}

/** Runs one import in the background, as a foreground service where Android allows one. */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = ImportNotifications.foreground(applicationContext, id, inputData.startedAt)

    override suspend fun doWork(): Result {
        val container = (applicationContext as AromaApplication).container
        if (container.sessions.state.first { it != SessionState.Loading } !is SessionState.SignedIn) return Result.success()
        try {
            setForeground(getForegroundInfo())
        } catch (_: IllegalStateException) {
            // Android lets no foreground service start from the background; the work runs on without one.
        }
        val request =
            ImportRequest(
                url = inputData.getString(URL).orEmpty(),
                useAi = inputData.getBoolean(USE_AI, false),
                translateTo = inputData.getString(TRANSLATE_TO),
            )
        val known = inputData.getStringArray(KNOWN).orEmpty().toSet()
        // Ended by Android in the middle of it, the import may have reached Mealie already: it is not sent twice.
        container.imports.run(id, request, known, inputData.startedAt, resumed = runAttemptCount > 0)
        // Success also when the import failed: that is told on its screen or in a notification, and
        // a failed import must not cancel the ones queued behind it.
        return Result.success()
    }

    private val Data.startedAt: Long get() = getLong(STARTED_AT, System.currentTimeMillis())

    companion object {
        private const val URL = "url"
        private const val USE_AI = "useAi"
        private const val TRANSLATE_TO = "translateTo"
        private const val KNOWN = "known"
        private const val STARTED_AT = "startedAt"

        fun input(request: ImportRequest, known: Set<String>, startedAt: Long): Data =
            workDataOf(
                URL to request.url,
                USE_AI to request.useAi,
                TRANSLATE_TO to request.translateTo,
                KNOWN to known.toTypedArray(),
                STARTED_AT to startedAt,
            )
    }
}
