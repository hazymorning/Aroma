package app.aroma.feature.editor

import android.content.Context
import android.net.Uri
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
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.data.AromaError.ImportFailed.Reason.NoRecipe
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.session.SessionState
import app.aroma.data.settings.inAppLanguage
import app.aroma.data.toAromaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.IOException
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

    /** One photo is prepared at a time, so the memory a large one takes never adds up. */
    private val preparing = Mutex()

    private fun state(id: UUID) = jobs.getOrPut(id) { MutableStateFlow(null) }

    /** Import [id] as it goes, collected by its screen while the screen is shown; null once the app was ended. */
    fun job(id: UUID): StateFlow<ImportJob?> = state(id)

    /**
     * Queues an import; it starts at once unless another one is running. Its photos are copied to
     * where they stay until the import ends, which the cache they waited in does not promise.
     */
    suspend fun start(request: ImportRequest): UUID {
        val id = UUID.randomUUID()
        val startedAt = System.currentTimeMillis()
        val queued = if (request.photos.isEmpty()) request else request.copy(photos = keep(request.photos, photosOf(id)))
        val work =
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setId(id)
                .setInputData(ImportWorker.input(queued, startedAt))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        state(id).value = ImportJob(queued, startedAt)
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        return id
    }

    /**
     * Scales a photo for an import down into the cache, where it waits for the import to start;
     * null when it cannot be read.
     */
    suspend fun preparePhoto(uri: Uri): File? =
        preparing.withLock {
            val photo = File(context.cacheDir, "$PHOTO_FOLDER/${UUID.randomUUID()}.jpg")
            val prepared = savePickedPhoto(context, uri, photo)
            // A photo just taken waited in the cache for this copy only.
            withContext(Dispatchers.IO) { forgetCameraPhoto(context, uri) }
            photo.takeIf { prepared }
        }

    /** Removes prepared photos nobody needs any more, in the background; an import has copies of its own. */
    fun discard(photos: List<File>) {
        if (photos.isNotEmpty()) Dispatchers.IO.asExecutor().execute { photos.forEach(File::delete) }
    }

    /** The screen has shown how import [id] ended; its notification is no longer needed. */
    fun shown(id: UUID) = ImportNotifications.dismiss(context, id)

    /** Drops the imports and reworks still queued, and the imports' photos, as when the user signs out. */
    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(QUEUE)
        File(context.noBackupFilesDir, PHOTO_FOLDER).deleteRecursively()
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
        // The newest recipes from right before the last and the first request that creates a recipe, kept on disk for a re-run.
        val sent = File(context.noBackupFilesDir, "imports/$id")
        val first = File(context.noBackupFilesDir, "imports/$id.first")
        val outcome =
            try {
                val known = if (rerun && sent.exists()) sent.ids() else null
                val onSending = { newest: Set<String> ->
                    sent.parentFile?.mkdirs()
                    if (!first.exists()) first.writeText(newest.joinToString("\n"))
                    sent.writeText(newest.joinToString("\n"))
                }
                val slug =
                    if (known != null) {
                        importer.resume(request, known, before = if (first.exists()) first.ids() else known, onSending, onStep)
                    } else {
                        importer.import(request, onSending, onStep)
                    }
                ImportOutcome.Added(slug, nameOf(slug))
            } catch (error: Exception) {
                // Whatever went wrong is told, rather than leaving the import running for good.
                ImportOutcome.Failed(error.toAromaError().ofPhotos(request))
            }
        sent.delete()
        first.delete()
        photosOf(id).deleteRecursively()
        val job = state.updateAndGet { it?.copy(outcome = outcome) } ?: return
        ImportNotifications.ended(context, id, job, seen())
    }

    private fun File.ids(): Set<String> = readLines().filter { it.isNotBlank() }.toSet()

    private fun photosOf(id: UUID) = File(context.noBackupFilesDir, "$PHOTO_FOLDER/$id")

    /** Copies an import's photos into [folder]; to the end, even when the screen that started the import closes meanwhile. */
    private suspend fun keep(photos: List<File>, folder: File): List<File> =
        withContext(NonCancellable + Dispatchers.IO) {
            photos.mapIndexedNotNull { index, photo ->
                try {
                    photo.copyTo(File(folder, "photo-${index + 1}.jpg"), overwrite = true)
                } catch (_: IOException) {
                    // Gone with the cache Android emptied, or no room left: the import goes on with the other photos.
                    null
                }
            }
        }

    /** A photo import that found no recipe says so of its photos, not of a page, where Mealie gave no reason of its own. */
    private fun AromaError.ofPhotos(request: ImportRequest): AromaError =
        if (request.photos.isNotEmpty() && this is AromaError.ImportFailed && reason == NoRecipe && detail == null) {
            AromaError.ImportFailed(detail = context.inAppLanguage().getString(R.string.add_photos_no_recipe))
        } else {
            this
        }

    private suspend fun nameOf(slug: String): String =
        try {
            (recipes.recipeJson(slug)["name"] as? JsonPrimitive)?.contentOrNull ?: slug
        } catch (_: AromaError) {
            slug
        }

    internal companion object {
        /**
         * Imports and reworks queue behind each other, so two never wait for a new recipe at the same
         * time: each would take the other's for its own.
         */
        const val QUEUE = "recipe-import"
        private const val PHOTO_FOLDER = "import-photos"
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
        container.imports.run(id, request(inputData), inputData.startedAt, rerun = runAttemptCount > 0)
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
        private const val PHOTOS = "photos"
        private const val STARTED_AT = "startedAt"

        /** As long as a caption may be; three bytes a character keep it within WorkManager's limit for the data of a work. */
        private const val MAX_SAVED_TEXT = 2400

        fun input(request: ImportRequest, startedAt: Long): Data =
            workDataOf(
                URL to request.url,
                TEXT to request.text.takeIf { it.length <= MAX_SAVED_TEXT },
                USE_AI to request.useAi,
                TRANSLATE_TO to request.translateTo,
                PHOTOS to request.photos.map { it.path }.toTypedArray(),
                STARTED_AT to startedAt,
            )

        /** The request as [input] keeps it. */
        internal fun request(data: Data): ImportRequest =
            ImportRequest(
                url = data.getString(URL).orEmpty(),
                text = data.getString(TEXT).orEmpty(),
                useAi = data.getBoolean(USE_AI, false),
                translateTo = data.getString(TRANSLATE_TO),
                photos = data.getStringArray(PHOTOS).orEmpty().map(::File),
            )
    }
}
