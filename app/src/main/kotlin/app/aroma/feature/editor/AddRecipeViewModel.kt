package app.aroma.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.format.SharedLink
import app.aroma.data.AromaError
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.UUID

/**
 * The add screen. An import runs as background work ([RecipeImports]); the screen follows it by
 * id, kept with its saved state, so it finds the import again after Android ended the app.
 * [sharedUrl] is a link shared to Aroma, [shownJob] an import opened from its notification.
 */
class AddRecipeViewModel(
    private val recipes: RecipeRepository,
    private val imports: RecipeImports,
    private val settings: SettingsRepository,
    sharedUrl: String?,
    shownJob: String?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /**
     * Whether a link shared from another app is imported at once. Any app can share to Aroma, so
     * only a web link starts without a tap; and only once, as Android brings the screen back with
     * the same link after it ended the app, when that import may be done already.
     */
    private val importsSharedLink = sharedUrl?.toHttpUrlOrNull() != null && saved.get<Boolean>(SHARED_LINK_IMPORTED) != true

    private val form = MutableStateFlow(AddRecipeUiState(url = sharedUrl.orEmpty(), starting = importsSharedLink))

    private val jobId = saved.getStateFlow(FOLLOWED_IMPORT, shownJob)

    /** Collected by the screen only while it is shown, which is how an import knows whether to notify. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AddRecipeUiState> =
        combine(form, jobId.flatMapLatest { id -> id?.let { imports.job(UUID.fromString(it)) } ?: flowOf(null) }) { form, job ->
            form.copy(import = job)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), form.value)

    private val outbox = Channel<String>(Channel.BUFFERED)

    /** Slugs of recipes created by hand, to open in the editor. */
    val created: Flow<String> = outbox.receiveAsFlow()

    init {
        if (importsSharedLink) saved[SHARED_LINK_IMPORTED] = true
        currentJob()?.request?.let { request -> form.update { it.copy(url = request.url, text = request.text) } }
        viewModelScope.launch {
            val preferences = settings.settings.first()
            form.update { it.copy(useAi = preferences.importWithAi, translate = preferences.translateImports) }
            // A link shared from another app starts right away, with the user's usual import settings;
            // whether the server has AI the import asks itself, so leaving the screen at once loses nothing.
            if (importsSharedLink) start()
            val aiAvailable = recipes.canImportWithAi()
            form.update { it.copy(aiAvailable = aiAvailable) }
        }
    }

    fun onUrlChange(text: String) = edit { it.copy(url = text) }

    fun onTextChange(text: String) = edit { it.copy(text = text) }

    fun onNameChange(text: String) = form.update { it.copy(name = text, createError = null) }

    fun onUseAiChange(enabled: Boolean) {
        edit { it.copy(useAi = enabled) }
        viewModelScope.launch { settings.setImportWithAi(enabled) }
    }

    fun onTranslateChange(enabled: Boolean) {
        form.update { it.copy(translate = enabled) }
        viewModelScope.launch { settings.setTranslateImports(enabled) }
    }

    fun import() {
        if (form.value.copy(import = currentJob()).canImport) start()
    }

    /** The screen showed how the import ended, so its notification can go. */
    fun onOutcomeShown() {
        jobId.value?.let { imports.shown(UUID.fromString(it)) }
    }

    fun create() {
        val current = form.value
        if (current.name.isBlank() || current.creating || current.starting) return
        viewModelScope.launch {
            form.update { it.copy(creating = true, createError = null) }
            try {
                outbox.send(recipes.create(current.name))
            } catch (error: AromaError) {
                form.update { it.copy(createError = error) }
            }
            form.update { it.copy(creating = false) }
        }
    }

    private fun start() {
        val current = form.value
        val request =
            ImportRequest(
                url = SharedLink.clean(current.url),
                text = current.text.trim(),
                useAi = current.useAi,
                translateTo = Locale.getDefault().toLanguageTag().takeIf { current.useAi && current.translate },
            )
        saved[FOLLOWED_IMPORT] = imports.start(request).toString()
        form.update { it.copy(starting = false) }
    }

    /** A change to what is imported; an import that ended no longer belongs to it. */
    private fun edit(change: (AddRecipeUiState) -> AddRecipeUiState) {
        if (currentJob()?.outcome != null) saved[FOLLOWED_IMPORT] = null
        form.update(change)
    }

    private fun currentJob(): ImportJob? = jobId.value?.let { imports.job(UUID.fromString(it)).value }
}

private const val FOLLOWED_IMPORT = "followedImport"
private const val SHARED_LINK_IMPORTED = "sharedLinkImported"
