package app.aroma.feature.editor

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeImage
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.messageRes
import coil3.compose.AsyncImage

data class EditorActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onChange: ((EditorForm) -> EditorForm) -> Unit = {},
    val onPickImage: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_cancel))
            }
            Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { if (!state.isSaving) actions.onSave() }, enabled = !state.isLoading && state.form.name.isNotBlank()) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            state.error != null && state.form.name.isEmpty() -> {
                ErrorState(
                    state.error,
                    onRetry = actions.onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Form(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(state: EditorUiState, actions: EditorActions) {
    val form = state.form
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { error ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                )
            }
        }
        Photo(state.newImage, state.currentImage, actions.onPickImage)
        Field(
            label = stringResource(R.string.editor_name),
            value = form.name,
            onValueChange = { value -> actions.onChange { it.copy(name = value) } },
        )
        Field(
            label = stringResource(R.string.editor_description),
            value = form.description,
            onValueChange = { value -> actions.onChange { it.copy(description = value) } },
            singleLine = false,
        )
        Field(
            label = stringResource(R.string.editor_servings),
            value = form.servings,
            onValueChange = { value -> actions.onChange { it.copy(servings = value) } },
            number = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = stringResource(R.string.editor_prep_minutes),
                value = form.prepMinutes,
                onValueChange = { value -> actions.onChange { it.copy(prepMinutes = value) } },
                number = true,
                modifier = Modifier.weight(1f),
            )
            Field(
                label = stringResource(R.string.editor_cook_minutes),
                value = form.cookMinutes,
                onValueChange = { value -> actions.onChange { it.copy(cookMinutes = value) } },
                number = true,
                modifier = Modifier.weight(1f),
            )
            Field(
                label = stringResource(R.string.editor_total_minutes),
                value = form.totalMinutes,
                onValueChange = { value -> actions.onChange { it.copy(totalMinutes = value) } },
                number = true,
                modifier = Modifier.weight(1f),
            )
        }
        Heading(stringResource(R.string.recipe_ingredients))
        Text(
            text = stringResource(R.string.editor_ingredients_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field(
            label = stringResource(R.string.recipe_ingredients),
            value = form.ingredients,
            onValueChange = { value -> actions.onChange { it.copy(ingredients = value) } },
            singleLine = false,
            minLines = 6,
        )
        Heading(stringResource(R.string.recipe_steps))
        form.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Field(
                    label = stringResource(R.string.editor_step, index + 1),
                    value = step,
                    onValueChange = { value ->
                        actions.onChange { current -> current.copy(steps = current.steps.toMutableList().also { it[index] = value }) }
                    },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (form.steps.size > 1) {
                    IconButton(onClick = {
                        actions.onChange { current -> current.copy(steps = current.steps.filterIndexed { i, _ -> i != index }) }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription =
                                stringResource(
                                    R.string.editor_remove_step,
                                    index + 1,
                                ),
                        )
                    }
                }
            }
        }
        TextButton(onClick = { actions.onChange { it.copy(steps = it.steps + "") } }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_add_step))
        }
        OrganizerPicker(stringResource(R.string.recipes_categories), state.allCategories, form.categories) { picked ->
            actions.onChange { it.copy(categories = picked) }
        }
        OrganizerPicker(
            stringResource(R.string.recipes_tags),
            state.allTags,
            form.tags,
        ) { picked -> actions.onChange { it.copy(tags = picked) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Photo(newImage: Uri?, current: RecipeImage?, onPick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large),
    ) {
        if (newImage != null) {
            AsyncImage(model = newImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            RecipeImage(current, ImageSize.Medium, modifier = Modifier.fillMaxSize(), fallbackIconSize = 40.dp)
        }
        FilledTonalButton(onClick = onPick, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
            Icon(painterResource(R.drawable.ic_add_photo_alternate), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_change_photo))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions =
            if (number) {
                KeyboardOptions(keyboardType = KeyboardType.Decimal)
            } else {
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerPicker(title: String, options: List<Organizer>, selected: List<Organizer>, onChange: (List<Organizer>) -> Unit) {
    if (options.isEmpty()) return
    Column {
        Heading(title)
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = selected.any { it.id == option.id }
                AromaFilterChip(
                    selected = isSelected,
                    label = option.name,
                    onClick = { onChange(if (isSelected) selected.filterNot { it.id == option.id } else selected + option) },
                )
            }
        }
    }
}
