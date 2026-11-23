package app.aroma.data.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** What is known about Aroma's newest release. */
sealed interface UpdateState {
    /** Not asked in this run of the app. */
    data object Unknown : UpdateState

    data object Checking : UpdateState

    /** This version is the newest. */
    data object Current : UpdateState

    /** [version] is out; [apkUrl] downloads it, [size] bytes long (0 where GitHub does not say). */
    data class Available(val version: String, val apkUrl: String, val size: Long) : UpdateState

    /** [update] is downloading; [progress] runs from 0 to 1. */
    data class Downloading(val update: Available, val progress: Float) : UpdateState

    /** GitHub could not be reached, or answered unexpectedly. */
    data object Failed : UpdateState
}

/** What the app says about an update outside of the settings. */
sealed interface UpdateMessage {
    /** Found when the app started. */
    data class Found(val update: UpdateState.Available) : UpdateMessage

    /** Downloaded and checked: ready for Android's installer, which asks before it replaces the app. */
    data class Install(val apk: File) : UpdateMessage

    data class NotDownloaded(val update: UpdateState.Available) : UpdateMessage

    /** The download is another app, which Android would install next to Aroma. */
    data class NotAroma(val version: String) : UpdateMessage
}

/**
 * Aroma's updates from its releases on GitHub: looked for when asked in the settings, or once at
 * start if that is turned on, never in the background. Android's installer checks that an update
 * carries Aroma's signing key; [isAroma] checks before that the download is Aroma at all.
 */
class Updates(
    private val http: OkHttpClient,
    private val currentVersion: String,
    private val folder: File,
    private val scope: CoroutineScope,
    private val isAroma: (File) -> Boolean,
    private val latestRelease: HttpUrl = LATEST_RELEASE.toHttpUrl(),
) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    private val outbox = Channel<UpdateMessage>(Channel.BUFFERED)
    val messages: Flow<UpdateMessage> = outbox.receiveAsFlow()

    private val checkedAtStart = AtomicBoolean(false)

    /** Once per run. A download from an earlier run goes first: it is installed by now, or was not wanted. */
    fun checkAtStart(enabled: suspend () -> Boolean) {
        if (!checkedAtStart.compareAndSet(false, true)) return
        scope.launch {
            withContext(Dispatchers.IO) { folder.deleteRecursively() }
            val found = if (enabled()) check() else return@launch
            if (found is UpdateState.Available) outbox.send(UpdateMessage.Found(found))
        }
    }

    fun checkNow() {
        val current = mutableState.value
        if (current != UpdateState.Checking && current !is UpdateState.Downloading) scope.launch { check() }
    }

    /** Downloads [update] and hands it to the installer; tapped again, the download is reused. */
    fun install(update: UpdateState.Available) {
        if (mutableState.value is UpdateState.Downloading) return
        mutableState.value = UpdateState.Downloading(update, 0f)
        scope.launch {
            val apk =
                try {
                    download(update)
                } catch (_: IOException) {
                    null
                }
            mutableState.value = update
            val message =
                when {
                    apk == null -> UpdateMessage.NotDownloaded(update)
                    withContext(Dispatchers.IO) { isAroma(apk) } -> UpdateMessage.Install(apk)
                    else -> UpdateMessage.NotAroma(update.version).also { apk.delete() }
                }
            outbox.send(message)
        }
    }

    private suspend fun check(): UpdateState {
        mutableState.value = UpdateState.Checking
        val found =
            try {
                latest()
            } catch (_: IOException) {
                UpdateState.Failed
            } catch (_: SerializationException) {
                UpdateState.Failed
            }
        mutableState.value = found
        return found
    }

    private suspend fun latest(): UpdateState =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(latestRelease)
                    .header("Accept", "application/vnd.github+json")
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
                val release = json.decodeFromString(Release.serializer(), response.body.string())
                val version = release.tag.removePrefix("v")
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apk != null && isNewer(version, currentVersion)) {
                    UpdateState.Available(version, apk.url, apk.size)
                } else {
                    UpdateState.Current
                }
            }
        }

    private suspend fun download(update: UpdateState.Available): File =
        withContext(Dispatchers.IO) {
            val apk = File(folder, "Aroma-${update.version}.apk")
            if (update.size > 0 && apk.length() == update.size) return@withContext apk
            folder.deleteRecursively()
            if (!folder.mkdirs()) throw IOException("No room for the update")
            val part = File(folder, "${apk.name}.part")
            http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: update.size
                var done = 0L
                var shown = 0f
                response.body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (done > MAX_BYTES) throw IOException("Larger than Aroma can be")
                            val progress = if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else 0f
                            if (progress - shown >= 0.01f) {
                                shown = progress
                                mutableState.value = UpdateState.Downloading(update, progress)
                            }
                        }
                    }
                }
                if (update.size > 0 && done != update.size) throw IOException("Cut short at $done of ${update.size} bytes")
            }
            if (!part.renameTo(apk)) throw IOException("The update could not be kept")
            apk
        }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
    )

    private companion object {
        /** Releases are published as regular releases, so the latest is the newest. */
        const val LATEST_RELEASE = "https://api.github.com/repos/hazymorning/Aroma/releases/latest"

        /** Far above the size of Aroma's APK: anything larger is not Aroma. */
        const val MAX_BYTES = 64L * 1024 * 1024

        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Whether version [candidate] comes after [current], comparing their numbers from the left. */
internal fun isNewer(candidate: String, current: String): Boolean {
    val new = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val old = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(new.size, old.size)) {
        val difference = new.getOrElse(i) { 0 } - old.getOrElse(i) { 0 }
        if (difference != 0) return difference > 0
    }
    return false
}
