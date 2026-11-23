package app.aroma.feature.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeImage
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LinkTone
import app.aroma.ui.components.OnPhotoButton
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PlaceholderLines
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import coil3.compose.AsyncImage

data class EditorActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onChange: ((EditorForm) -> EditorForm) -> Unit = {},
    val onPickImage: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun EditorScreen(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    DetailPage(
        title = stringResource(R.string.editor_title),
        onBack = actions.onBack,
        modifier = modifier,
        scroll = scroll,
        actions = {
            SaveLink(
                saving = state.isSaving,
                enabled = !state.isLoading && state.form.name.isNotBlank(),
                onSave = actions.onSave,
            )
        },
    ) {
        when {
            state.isLoading -> {
                Loading()
            }

            state.error != null && state.form.name.isEmpty() -> {
                ErrorState(state.error, onRetry = actions.onRetry)
            }

            else -> {
                Form(state, actions, scroll)
            }
        }
    }
}

/** Save as a quiet ink link; while saving, a small spinner holds its place. */
@Composable
private fun SaveLink(saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
    // The bar already insets its actions by 8 dp; 16 more puts the text on the right margin.
    Box(Modifier.padding(end = Space.m), contentAlignment = Alignment.Center) {
        QuietLink(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            tone = LinkTone.Ink,
            enabled = enabled && !saving,
            modifier = Modifier.alpha(if (saving) 0f else 1f),
        )
        if (saving) InlineProgress()
    }
}

@Composable
private fun Loading() {
    Column(Modifier.padding(top = Space.l)) {
        Placeholder(
            Modifier
                .padding(horizontal = ScreenMargin)
                .fillMaxWidth()
                .aspectRatio(PHOTO_RATIO),
        )
        Spacer(Modifier.height(Space.l))
        PlaceholderLines(3, Modifier.padding(horizontal = ScreenMargin))
    }
}

@Composable
private fun Form(state: EditorUiState, actions: EditorActions, scroll: ScrollState) {
    val form = state.form
    val change = actions.onChange
    // A failed save is reported under the title, so bring it into view.
    LaunchedEffect(state.error) { if (state.error != null) scroll.animateScrollTo(0) }
    Column {
        state.error?.let { InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m)) }
        Spacer(Modifier.height(Space.l))
        Photo(
            newImage = state.newImage,
            current = state.currentImage,
            onPick = actions.onPickImage,
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Spacer(Modifier.height(Space.l))
        Basics(form, change)
        Spacer(Modifier.height(Space.xxl))
        SectionHeader(stringResource(R.string.recipe_ingredients))
        Text(
            text = stringResource(R.string.editor_ingredients_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        EditableRows(
            rows = form.ingredients.lines(),
            onChange = { lines -> change { it.copy(ingredients = lines.joinToString("\n")) } },
            placeholder = stringResource(R.string.editor_ingredient_hint),
            addLabel = stringResource(R.string.editor_add_ingredient),
            removeLabel = R.string.editor_remove_ingredient,
            numbered = false,
        )
        Spacer(Modifier.height(Space.xxl))
        SectionHeader(stringResource(R.string.recipe_steps))
        EditableRows(
            rows = form.steps,
            onChange = { steps -> change { it.copy(steps = steps) } },
            placeholder = stringResource(R.string.editor_step_hint),
            addLabel = stringResource(R.string.editor_add_step),
            removeLabel = R.string.editor_remove_step,
            numbered = true,
        )
        OrganizerPicker(stringResource(R.string.recipes_categories), state.allCategories, form.categories) { picked ->
            change { it.copy(categories = picked) }
        }
        OrganizerPicker(stringResource(R.string.recipes_tags), state.allTags, form.tags) { picked ->
            change { it.copy(tags = picked) }
        }
    }
}

/** Name, description, servings and times: the facts at the top of a recipe. */
@Composable
private fun Basics(form: EditorForm, change: ((EditorForm) -> EditorForm) -> Unit) {
    Column(Modifier.padding(horizontal = ScreenMargin), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        LabeledTextField(
            value = form.name,
            onValueChange = { value -> change { it.copy(name = value) } },
            label = stringResource(R.string.editor_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        LabeledTextField(
            value = form.description,
            onValueChange = { value -> change { it.copy(description = value) } },
            label = stringResource(R.string.editor_description),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            NumberField(
                label = stringResource(R.string.editor_servings),
                value = form.servings,
                onValueChange = { value -> change { it.copy(servings = value) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.editor_prep_minutes),
                value = form.prepMinutes,
                onValueChange = { value -> change { it.copy(prepMinutes = value) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            NumberField(
                label = stringResource(R.string.editor_cook_minutes),
                value = form.cookMinutes,
                onValueChange = { value -> change { it.copy(cookMinutes = value) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.editor_total_minutes),
                value = form.totalMinutes,
                onValueChange = { value -> change { it.copy(totalMinutes = value) } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    LabeledTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/**
 * The photo at 4:3 with a paper button to change it, or, without one yet, a quiet invitation to
 * choose it.
 */
@Composable
private fun Photo(newImage: Uri?, current: RecipeImage?, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_RATIO)
            .clip(MaterialTheme.shapes.medium),
    ) {
        when {
            newImage != null -> {
                AsyncImage(model = newImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            current != null -> {
                RecipeImage(current, ImageSize.Medium, modifier = Modifier.fillMaxSize(), fallbackIconSize = 32.dp)
            }

            else -> {
                NoPhoto(onPick)
            }
        }
        if (newImage != null || current != null) {
            OnPhotoButton(
                icon = R.drawable.ic_add_photo_alternate,
                contentDescription = stringResource(R.string.editor_change_photo),
                onClick = onPick,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.xs),
            )
        }
    }
}

/** The empty photo: the plate a missing picture shows everywhere, and the whole area picks one. */
@Composable
private fun NoPhoto(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onPick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_plate),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.editor_pick_photo), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Ingredients and steps as rows of plain text between hairlines, each with a quiet remove glyph,
 * and a link to add one more. Adding a row, or pressing next on the keyboard in the last
 * ingredient, moves the cursor into the new row.
 */
@Composable
private fun EditableRows(
    rows: List<String>,
    onChange: (List<String>) -> Unit,
    placeholder: String,
    addLabel: String,
    @StringRes removeLabel: Int,
    numbered: Boolean,
) {
    val keys = remember { RowKeys() }
    val rowKeys = keys.of(rows.size)
    var focusRow by remember { mutableStateOf<Int?>(null) }
    val addRow = {
        onChange(rows + "")
        focusRow = rows.size
    }
    Column {
        rows.forEachIndexed { index, text ->
            key(rowKeys[index]) {
                val focusRequester = remember { FocusRequester() }
                // A new row arrives with the next state; the cursor moves into it once it is here.
                if (focusRow == index) {
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        focusRow = null
                    }
                }
                if (index > 0) Hairline(Modifier.padding(horizontal = ScreenMargin))
                EditableRow(
                    text = text,
                    onTextChange = { value -> onChange(rows.toMutableList().also { it[index] = value }) },
                    placeholder = placeholder,
                    number = if (numbered) index + 1 else null,
                    removeLabel = stringResource(removeLabel, index + 1),
                    onRemove =
                        if (rows.size > 1) {
                            {
                                keys.remove(index)
                                onChange(rows.filterIndexed { i, _ -> i != index })
                            }
                        } else {
                            null
                        },
                    onNext =
                        if (numbered) {
                            null
                        } else {
                            {
                                if (index == rows.lastIndex) {
                                    addRow()
                                } else {
                                    focusRow = index + 1
                                }
                            }
                        },
                    focusRequester = focusRequester,
                )
            }
        }
        QuietLink(addLabel, addRow, Modifier.padding(horizontal = ScreenMargin), tone = LinkTone.Ink, leadingIcon = R.drawable.ic_add)
    }
}

/**
 * A key per row that stays with it when a row before it is removed, so each field keeps its text,
 * cursor and focus. Rows that arrive another way, such as lines pasted into one, get new keys.
 */
private class RowKeys {
    private val keys = mutableListOf<Int>()
    private var next = 0

    fun of(rows: Int): List<Int> {
        while (keys.size < rows) keys += next++
        while (keys.size > rows) keys.removeAt(keys.lastIndex)
        return keys
    }

    fun remove(row: Int) {
        keys.removeAt(row)
    }
}

/**
 * One row of [EditableRows]. Steps carry their number in the gutter, like the recipe page.
 * Ingredients stay on one line of thought: the keyboard's action moves on instead of breaking it.
 */
@Composable
private fun EditableRow(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    number: Int?,
    removeLabel: String,
    onRemove: (() -> Unit)?,
    onNext: (() -> Unit)?,
    focusRequester: FocusRequester,
) {
    val body = MaterialTheme.typography.bodyLarge
    // Lines starting with # become section headings; setting them in Medium shows that while typing.
    val style = if (text.trimStart().startsWith("#")) body.copy(fontWeight = FontWeight.Medium) else body
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ScreenMargin, end = if (onRemove != null) Space.xs else ScreenMargin),
        verticalAlignment = Alignment.Top,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (onNext != null) ImeAction.Next else ImeAction.Default,
                ),
            keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                // The whole 56 dp row takes the tap, not just the line of text.
                Row(
                    Modifier
                        .heightIn(min = 56.dp)
                        .padding(vertical = Space.m),
                ) {
                    if (number != null) StepNumber(number)
                    Box(
                        Modifier
                            .weight(1f)
                            .alignByBaseline(),
                    ) {
                        if (text.isEmpty()) {
                            Text(placeholder, style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                }
            },
        )
        if (onRemove != null) {
            // Level with the first line of text.
            IconAction(R.drawable.ic_close, removeLabel, onRemove, Modifier.padding(top = Space.xxs))
        }
    }
}

@Composable
private fun RowScope.StepNumber(number: Int) {
    val label = stringResource(R.string.editor_step, number)
    Text(
        text = number.toString(),
        style =
            MaterialTheme.typography.titleLarge
                .copy(lineHeight = 24.sp)
                .tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                // Step text starts on the 64 dp keyline, as text after an icon does.
                .width(IconKeyline - ScreenMargin)
                .alignByBaseline()
                .semantics { contentDescription = label },
    )
}

@Composable
private fun OrganizerPicker(title: String, options: List<Organizer>, selected: List<Organizer>, onChange: (List<Organizer>) -> Unit) {
    if (options.isEmpty()) return
    Column(Modifier.padding(top = Space.xxl)) {
        SectionHeader(title)
        FlowRow(
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
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

private const val PHOTO_RATIO = 4f / 3f
