package app.aroma.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeImage
import app.aroma.data.AromaError
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

data class EditorUiState(
    val form: EditorForm = EditorForm(),
    val allCategories: List<Organizer> = emptyList(),
    val allTags: List<Organizer> = emptyList(),
    val currentImage: RecipeImage? = null,
    val newImage: Uri? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: AromaError? = null,
)

class EditorViewModel(
    private val slug: String,
    private val recipes: RecipeRepository,
    private val serverUrl: String,
    private val locale: Locale,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    private val mutableSaved = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the (possibly renamed) slug after a successful save. */
    val saved: SharedFlow<String> = mutableSaved.asSharedFlow()

    private var original: JsonObject? = null
    private var image: Pair<ByteArray, String>? = null

    init {
        load()
        viewModelScope.launch {
            recipes.organizers().catch { }.collect { loadable ->
                mutableState.update { it.copy(allCategories = loadable.data.categories, allTags = loadable.data.tags) }
            }
        }
    }

    fun load() =
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            runCatching { recipes.recipeJson(slug) }
                .onSuccess { json ->
                    original = json
                    val image = (json["image"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    val id = (json["id"] as? JsonPrimitive)?.contentOrNull
                    mutableState.update {
                        it.copy(
                            form = RecipeDraft.formFrom(json, locale),
                            currentImage = if (image != null && id != null) RecipeImage(serverUrl, id, image) else null,
                            isLoading = false,
                        )
                    }
                }.onFailure { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
        }

    fun update(change: (EditorForm) -> EditorForm) = mutableState.update { it.copy(form = change(it.form), error = null) }

    fun setImage(uri: Uri, bytes: ByteArray, extension: String) {
        image = bytes to extension
        mutableState.update { it.copy(newImage = uri) }
    }

    fun save(durationText: (Int) -> String) {
        val json = original ?: return
        if (state.value.isSaving) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val patch = RecipeDraft.patch(json, state.value.form, locale, durationText)
                val newSlug = if (patch.isEmpty()) slug else recipes.update(slug, patch)
                image?.let { (bytes, extension) -> recipes.uploadImage(newSlug, bytes, extension) }
                newSlug
            }.onSuccess { mutableSaved.emit(it) }
                .onFailure { error -> mutableState.update { it.copy(error = error.toAromaError()) } }
            mutableState.update { it.copy(isSaving = false) }
        }
    }
}
