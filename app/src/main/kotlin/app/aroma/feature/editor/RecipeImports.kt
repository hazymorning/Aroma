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
import java.io.File
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

    /** Queues an import; it starts at once unless another one is running. */
    fun start(request: ImportRequest): UUID {
        val startedAt = System.currentTimeMillis()
        val work =
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(ImportWorker.input(request, startedAt))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        state(work.id).value = ImportJob(request, startedAt)
        // Imports queue behind each other, so two never wait for a new recipe at the same time.
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        return work.id
    }

    /** The screen has shown how import [id] ended; its notification is no longer needed. */
    fun shown(id: UUID) = ImportNotifications.dismiss(context, id)

    /** Drops the imports still queued, as when the user signs out. */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(QUEUE)
    }

    /**
     * Runs import [id] for its worker. [saved] is the request as the worker keeps it; a pasted
     * text too long for it is taken from the running app. [rerun] is set when Android ended the
     * app during an earlier run: if that run had sent its request already, Mealie may be saving
     * the recipe, so the import waits for it instead of sending the request again.
     */
    internal suspend fun run(id: UUID, saved: ImportRequest, startedAt: Long, rerun: Boolean) {
        val state = state(id)
        val request = state.value?.request ?: saved
        state.value = ImportJob(request, startedAt)
        // A screen collects its import only while it is shown: nobody collecting means nobody sees it.
        val seen = { state.subscriptionCount.value > 0 }
        val onStep = { step: ImportStep ->
            val job = state.updateAndGet { it?.next(step) }
            if (job != null && !seen()) ImportNotifications.running(context, id, job)
        }
        // The newest recipes from right before the request went out, kept on disk for a re-run.
        val sent = File(context.noBackupFilesDir, "imports/$id")
        val outcome =
            try {
                val known = if (rerun && sent.exists()) sent.readLines().filter { it.isNotBlank() }.toSet() else null
                val onSending = { newest: Set<String> ->
                    sent.parentFile?.mkdirs()
                    sent.writeText(newest.joinToString("\n"))
                }
                val slug =
                    if (known != null) {
                        importer.resume(request, known, onSending, onStep)
                    } else {
                        importer.import(request, onSending, onStep)
                    }
                ImportOutcome.Added(slug, nameOf(slug))
            } catch (error: Exception) {
                // Whatever went wrong is told, rather than leaving the import running for good.
                ImportOutcome.Failed(error.toAromaError())
            }
        sent.delete()
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
                text = inputData.getString(TEXT).orEmpty(),
                useAi = inputData.getBoolean(USE_AI, false),
                translateTo = inputData.getString(TRANSLATE_TO),
            )
        container.imports.run(id, request, inputData.startedAt, rerun = runAttemptCount > 0)
        // Success also when the import failed: that is told on its screen or in a notification, and
        // a failed import must not cancel the ones queued behind it.
        return Result.success()
    }

    private val Data.startedAt: Long get() = getLong(STARTED_AT, System.currentTimeMillis())

    companion object {
        private const val URL = "url"
        private const val TEXT = "text"
        private const val USE_AI = "useAi"
        private const val TRANSLATE_TO = "translateTo"
        private const val STARTED_AT = "startedAt"

        /** As long as a caption may be; three bytes a character keep it within WorkManager's limit for the data of a work. */
        private const val MAX_SAVED_TEXT = 2400

        fun input(request: ImportRequest, startedAt: Long): Data =
            workDataOf(
                URL to request.url,
                TEXT to request.text.takeIf { it.length <= MAX_SAVED_TEXT },
                USE_AI to request.useAi,
                TRANSLATE_TO to request.translateTo,
                STARTED_AT to startedAt,
            )
    }
}
