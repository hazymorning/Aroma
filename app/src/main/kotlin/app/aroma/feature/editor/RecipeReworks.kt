package app.aroma.feature.editor

import android.content.Context
import android.content.res.Resources
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.data.toAromaError
import app.aroma.ui.components.titleRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The recipe to rework, with its [slug] and [name] as they were then, and the language to translate it into, if any. */
data class ReworkRequest(
    val recipeId: String,
    val slug: String,
    val name: String,
    val translateTo: String?,
)

/** A rework as the recipe's page and the notifications show it. [startedAt] is wall-clock time in milliseconds. */
data class ReworkJob(
    val id: UUID,
    val request: ReworkRequest,
    val startedAt: Long,
    val steps: List<ImportStep> = emptyList(),
    val outcome: ReworkOutcome? = null,
) {
    /** This rework at [step]; a step reported again stays one line. */
    fun next(step: ImportStep): ReworkJob = if (steps.lastOrNull() == step) this else copy(steps = steps + step)
}

sealed interface ReworkOutcome {
    /** The recipe, now under [slug], took the AI's version; [undo] puts back what that replaced. */
    data class Reworked(val slug: String, val undo: JsonObject) : ReworkOutcome

    data class Failed(val error: AromaError) : ReworkOutcome
}

/** Why a rework failed, in a few words: Mealie's own, where it gave some. */
internal fun AromaError.reworkReason(resources: Resources): String =
    when (this) {
        is AromaError.ImportFailed -> detail ?: resources.getString(R.string.recipe_rework_no_recipe)
        else -> resources.getString(titleRes())
    }

/**
 * Runs reworks as background work, in the queue of the imports. A rework goes on when its recipe's
 * page is left, with a notification while it runs, and tells how it ended in another one unless the
 * page is open to show it. Pages follow the rework of their recipe by the recipe's id, which stays
 * when a translated name gives the recipe a new slug.
 */
class RecipeReworks(private val context: Context, private val reworker: RecipeReworker) {
    private val jobs = ConcurrentHashMap<String, MutableStateFlow<ReworkJob?>>()

    private fun state(recipeId: String) = jobs.getOrPut(recipeId) { MutableStateFlow(null) }

    /** The rework of the recipe with [recipeId] as it goes, collected by the recipe's page while it is shown; null without one. */
    fun job(recipeId: String): StateFlow<ReworkJob?> = state(recipeId)

    /** Queues a rework; it starts at once unless an import or another rework is running. */
    fun start(request: ReworkRequest) {
        val startedAt = System.currentTimeMillis()
        val work =
            OneTimeWorkRequestBuilder<ReworkWorker>()
                .setInputData(ReworkWorker.input(request, startedAt))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        state(request.recipeId).value = ReworkJob(work.id, request, startedAt)
        WorkManager.getInstance(context).enqueueUniqueWork(RecipeImports.QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    /** The recipe's page showed how its rework ended, so the rework and its notification are done with. */
    fun shown(recipeId: String) {
        val state = state(recipeId)
        state.value?.let { ImportNotifications.dismiss(context, it.id) }
        state.value = null
    }

    /** Forgets all reworks, as when the user signs out and [RecipeImports.cancelAll] drops the work that runs them. */
    fun forgetAll() = jobs.values.forEach { it.value = null }

    /**
     * Runs rework [id] for its worker. [rerun] is set when Android ended the app during an earlier
     * run: if that run had sent its request already, Mealie may be making the draft, so the rework
     * waits for it instead of sending the request again.
     */
    internal suspend fun run(id: UUID, request: ReworkRequest, startedAt: Long, rerun: Boolean) {
        val state = state(request.recipeId)
        state.value = ReworkJob(id, request, startedAt)
        // The page collects its rework only while it is shown: nobody collecting means nobody sees it.
        val seen = { state.subscriptionCount.value > 0 }
        val onStep = { step: ImportStep ->
            val job = state.updateAndGet { it?.next(step) }
            if (job != null && !seen()) ImportNotifications.running(context, job)
        }
        // The newest recipes from right before the request that makes the draft, kept on disk for a re-run.
        val sent = File(context.noBackupFilesDir, "reworks/$id")
        val outcome =
            try {
                if (rerun && sent.exists()) {
                    val known = sent.readLines().filter { it.isNotBlank() }.toSet()
                    reworker.resume(request.recipeId, translated = request.translateTo != null, known, onStep)
                } else {
                    val onSending = { newest: Set<String> ->
                        sent.parentFile?.mkdirs()
                        sent.writeText(newest.joinToString("\n"))
                    }
                    reworker.rework(request.recipeId, request.translateTo, onSending, onStep)
                }
            } catch (error: Exception) {
                // Whatever went wrong is told, rather than leaving the rework running for good.
                ReworkOutcome.Failed(error.toAromaError())
            }
        sent.delete()
        val job = state.updateAndGet { it?.copy(outcome = outcome) } ?: return
        ImportNotifications.ended(context, job, seen())
    }
}

/** Runs one rework in the background, as a foreground service where Android allows one. */
class ReworkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ImportNotifications.foreground(applicationContext, ReworkJob(id, request(inputData), inputData.startedAt))

    override suspend fun doWork(): Result =
        runSignedIn { it.reworks.run(id, request(inputData), inputData.startedAt, rerun = runAttemptCount > 0) }

    private val Data.startedAt: Long get() = getLong(STARTED_AT, System.currentTimeMillis())

    companion object {
        private const val RECIPE_ID = "recipeId"
        private const val SLUG = "slug"
        private const val NAME = "name"
        private const val TRANSLATE_TO = "translateTo"
        private const val STARTED_AT = "startedAt"

        fun input(request: ReworkRequest, startedAt: Long): Data =
            workDataOf(
                RECIPE_ID to request.recipeId,
                SLUG to request.slug,
                NAME to request.name,
                TRANSLATE_TO to request.translateTo,
                STARTED_AT to startedAt,
            )

        /** The request as [input] keeps it. */
        private fun request(data: Data): ReworkRequest =
            ReworkRequest(
                recipeId = data.getString(RECIPE_ID).orEmpty(),
                slug = data.getString(SLUG).orEmpty(),
                name = data.getString(NAME).orEmpty(),
                translateTo = data.getString(TRANSLATE_TO),
            )
    }
}
