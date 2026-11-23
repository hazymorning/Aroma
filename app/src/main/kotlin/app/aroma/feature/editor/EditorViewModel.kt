package app.aroma.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeImage
import app.aroma.data.AromaError
import app.aroma.data.attempt
import app.aroma.data.remote.MealieJson
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    private var slug: String,
    private val recipes: RecipeRepository,
    private val serverUrl: String,
    private val locale: Locale,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    private val outbox = Channel<String>(Channel.BUFFERED)

    /** Emits the (possibly renamed) slug after a successful save. */
    val saved: Flow<String> = outbox.receiveAsFlow()

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
            attempt { recipes.recipeJson(slug) }
                .onSuccess { json ->
                    original = json
                    val image = (json["image"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    val id = (json["id"] as? JsonPrimitive)?.contentOrNull
                    // Changes made before Android ended the app come back instead of the server's copy.
                    val edited = savedState.get<String>(FORM)?.let { MealieJson.decodeFromString(EditorForm.serializer(), it) }
                    mutableState.update {
                        it.copy(
                            form = edited ?: RecipeDraft.formFrom(json, locale),
                            currentImage = if (image != null && id != null) RecipeImage(serverUrl, id, image) else null,
                            isLoading = false,
                        )
                    }
                }.onFailure { error -> mutableState.update { it.copy(isLoading = false, error = error.toAromaError()) } }
        }

    fun update(change: (EditorForm) -> EditorForm) {
        val form = mutableState.updateAndGet { it.copy(form = change(it.form), error = null) }.form
        savedState[FORM] = MealieJson.encodeToString(EditorForm.serializer(), form)
    }

    fun setImage(uri: Uri, bytes: ByteArray, extension: String) {
        image = bytes to extension
        mutableState.update { it.copy(newImage = uri) }
    }

    fun save(durationText: (Int) -> String) {
        val json = original ?: return
        if (state.value.isSaving) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            attempt {
                val patch = RecipeDraft.patch(json, state.value.form, locale, durationText)
                if (patch.isNotEmpty()) {
                    slug = recipes.update(slug, patch)
                    // Saved even if the photo fails next: a retry starts from here, under the new slug.
                    original = JsonObject(json + patch)
                }
                image?.let { (bytes, extension) -> recipes.uploadImage(slug, bytes, extension) }
                slug
            }.onSuccess { outbox.send(it) }
                .onFailure { error -> mutableState.update { it.copy(error = error.toAromaError()) } }
            mutableState.update { it.copy(isSaving = false) }
        }
    }
}

private const val FORM = "form"
