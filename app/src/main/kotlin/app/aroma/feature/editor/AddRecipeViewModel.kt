package app.aroma.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where to go after a recipe was added: imported recipes open directly, new ones in the editor. */
sealed interface AddRecipeResult {
    data class Imported(val slug: String) : AddRecipeResult

    data class Created(val slug: String) : AddRecipeResult
}

class AddRecipeViewModel(private val recipes: RecipeRepository, sharedUrl: String?) : ViewModel() {
    private val mutableState = MutableStateFlow(AddRecipeUiState(url = sharedUrl.orEmpty()))
    val state: StateFlow<AddRecipeUiState> = mutableState.asStateFlow()

    private val mutableResults = MutableSharedFlow<AddRecipeResult>(extraBufferCapacity = 1)
    val results: SharedFlow<AddRecipeResult> = mutableResults.asSharedFlow()

    init {
        if (!sharedUrl.isNullOrBlank()) import()
    }

    fun onUrlChange(text: String) = mutableState.update { it.copy(url = text, error = null) }

    fun onNameChange(text: String) = mutableState.update { it.copy(name = text, error = null) }

    fun import() = run(AddMode.Import) { AddRecipeResult.Imported(recipes.importFromUrl(state.value.url)) }

    fun create() = run(AddMode.Create) { AddRecipeResult.Created(recipes.create(state.value.name)) }

    private fun run(mode: AddMode, block: suspend () -> AddRecipeResult) {
        if (state.value.working != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(working = mode, error = null) }
            runCatching { block() }
                .onSuccess { mutableResults.emit(it) }
                .onFailure { error -> mutableState.update { it.copy(error = error.toAromaError()) } }
            mutableState.update { it.copy(working = null) }
        }
    }
}
