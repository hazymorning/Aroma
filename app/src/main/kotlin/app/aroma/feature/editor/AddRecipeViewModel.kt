package app.aroma.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.format.SharedLink
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Where to go after a recipe was added: imported recipes open directly, new ones in the editor. */
sealed interface AddRecipeResult {
    data class Imported(val slug: String) : AddRecipeResult

    data class Created(val slug: String) : AddRecipeResult
}

class AddRecipeViewModel(
    private val recipes: RecipeRepository,
    private val importer: RecipeImporter,
    private val settings: SettingsRepository,
    sharedUrl: String?,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            if (sharedUrl.isNullOrBlank()) {
                AddRecipeUiState()
            } else {
                AddRecipeUiState(url = sharedUrl, working = AddMode.Import, step = ImportStep.Waiting)
            },
        )
    val state: StateFlow<AddRecipeUiState> = mutableState.asStateFlow()

    private val mutableResults = MutableSharedFlow<AddRecipeResult>(extraBufferCapacity = 1)
    val results: SharedFlow<AddRecipeResult> = mutableResults.asSharedFlow()

    init {
        viewModelScope.launch {
            val preferences = settings.settings.first()
            val aiAvailable = recipes.canImportWithAi()
            mutableState.update {
                it.copy(aiAvailable = aiAvailable, useAi = preferences.importWithAi, translate = preferences.translateImports)
            }
            // A link shared from another app starts right away, with the user's usual import settings.
            if (!sharedUrl.isNullOrBlank()) start(AddMode.Import) { importNow() }
        }
    }

    fun onUrlChange(text: String) = mutableState.update { it.copy(url = text, error = null) }

    fun onTextChange(text: String) = mutableState.update { it.copy(text = text, error = null) }

    fun onNameChange(text: String) = mutableState.update { it.copy(name = text, error = null) }

    fun onUseAiChange(enabled: Boolean) {
        mutableState.update { it.copy(useAi = enabled, error = null) }
        viewModelScope.launch { settings.setImportWithAi(enabled) }
    }

    fun onTranslateChange(enabled: Boolean) {
        mutableState.update { it.copy(translate = enabled) }
        viewModelScope.launch { settings.setTranslateImports(enabled) }
    }

    fun import() {
        if (state.value.canImport) start(AddMode.Import) { importNow() }
    }

    fun create() {
        if (state.value.working == null && state.value.name.isNotBlank()) {
            start(AddMode.Create) { AddRecipeResult.Created(recipes.create(state.value.name)) }
        }
    }

    private suspend fun importNow(): AddRecipeResult {
        val current = state.value
        val request =
            ImportRequest(
                url = SharedLink.clean(current.url),
                text = if (current.usesAi) current.text else "",
                useAi = current.usesAi,
                translateTo = Locale.getDefault().toLanguageTag().takeIf { current.usesAi && current.translate },
            )
        // The import runs on in the app's scope: going back only stops the waiting here, while the
        // rebuild and the cleanup of half-finished recipes on the server still complete.
        val import = appScope.async { importer.import(request) { step -> mutableState.update { it.copy(step = step) } } }
        return AddRecipeResult.Imported(import.await())
    }

    private fun start(mode: AddMode, block: suspend () -> AddRecipeResult) {
        viewModelScope.launch {
            mutableState.update { it.copy(working = mode, error = null) }
            runCatching { block() }
                .onSuccess { mutableResults.emit(it) }
                .onFailure { error -> mutableState.update { it.copy(error = error.toAromaError()) } }
            mutableState.update { it.copy(working = null, step = null) }
        }
    }
}
