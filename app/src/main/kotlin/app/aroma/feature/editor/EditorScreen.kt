package app.aroma.feature.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.aroma.R
import app.aroma.core.model.Filing
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Organizer
import app.aroma.core.model.RecipeImage
import app.aroma.data.AromaError
import app.aroma.ui.components.BottomActionBar
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.ErrorNote
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonPlace
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SectionTitle
import app.aroma.ui.components.Separator
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import coil3.compose.AsyncImage

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
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = actions.onSave,
                    enabled = !state.isLoading && state.form.name.isNotBlank(),
                    loading = state.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
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

@Composable
private fun Loading() {
    Column {
        Skeleton(
            Modifier
                .padding(horizontal = PageMargin)
                .fillMaxWidth()
                .aspectRatio(PHOTO_RATIO),
            shape = AromaTheme.shapes.cardImage,
        )
        Spacer(Modifier.height(Space.s24))
        Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
            listOf(1f, 1f, 0.5f).forEach { SkeletonLine(Modifier.fillMaxWidth(it)) }
        }
    }
}

@Composable
private fun Form(state: EditorUiState, actions: EditorActions, scroll: ScrollState) {
    val form = state.form
    val change = actions.onChange
    // A failed save is reported under the title, so bring it into view.
    LaunchedEffect(state.error) { if (state.error != null) scroll.animateScrollTo(0) }
    Column {
        state.error?.let { ErrorNote(it, Modifier.padding(start = PageMargin, end = PageMargin, bottom = Space.s24)) }
        Photo(
            newImage = state.newImage,
            current = state.currentImage,
            onPick = actions.onPickImage,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        Spacer(Modifier.height(Space.s24))
        Basics(form, change)
        SectionTitle(stringResource(R.string.organizers_meal), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        Text(
            text = stringResource(R.string.organizers_meal_hint),
            style = AromaTheme.type.bodySmall,
            color = AromaTheme.colors.inkSecondary,
            modifier = Modifier.padding(start = PageMargin, end = PageMargin, bottom = Space.s4),
        )
        MealChips(
            chosen = Filing(form.categories, form.tags).meals,
            onToggle = { meal, label ->
                change { it.filedAs(Filing(it.categories, it.tags).toggle(meal, state.allCategories, label)) }
            },
        )
        SectionTitle(stringResource(R.string.recipe_ingredients), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        Text(
            text = stringResource(R.string.editor_ingredients_hint),
            style = AromaTheme.type.bodySmall,
            color = AromaTheme.colors.inkSecondary,
            modifier = Modifier.padding(start = PageMargin, end = PageMargin, bottom = Space.s12),
        )
        EditableRows(
            rows = form.ingredients.lines(),
            onChange = { lines -> change { it.copy(ingredients = lines.joinToString("\n")) } },
            placeholder = stringResource(R.string.editor_ingredient_hint),
            addLabel = stringResource(R.string.editor_add_ingredient),
            removeLabel = R.string.editor_remove_ingredient,
            numbered = false,
        )
        SectionTitle(stringResource(R.string.recipe_steps), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        EditableRows(
            rows = form.steps,
            onChange = { steps -> change { it.copy(steps = steps) } },
            placeholder = stringResource(R.string.editor_step_hint),
            addLabel = stringResource(R.string.editor_add_step),
            removeLabel = R.string.editor_remove_step,
            numbered = true,
        )
        SectionTitle(stringResource(R.string.recipes_categories), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        OrganizerChips(
            options = state.allCategories,
            selected = form.categories,
            onChange = { picked -> change { it.copy(categories = picked) } },
            newLabel = stringResource(R.string.organizers_new_category),
            modifier = Modifier.padding(top = Space.s8),
        )
        SectionTitle(stringResource(R.string.recipes_tags), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        OrganizerChips(
            options = state.allTags,
            selected = form.tags,
            onChange = { picked -> change { it.copy(tags = picked) } },
            newLabel = stringResource(R.string.organizers_new_tag),
            modifier = Modifier.padding(top = Space.s8),
        )
    }
}

/** Name, description, servings and times: the facts at the top of a recipe. */
@Composable
private fun Basics(form: EditorForm, change: ((EditorForm) -> EditorForm) -> Unit) {
    Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
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
        // A label that takes two lines at a large font leaves the fields side by side on one line.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalAlignment = Alignment.Bottom) {
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
        // A label that takes two lines at a large font leaves the fields side by side on one line.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s16), verticalAlignment = Alignment.Bottom) {
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
            .clip(AromaTheme.shapes.cardImage),
    ) {
        when {
            newImage != null -> {
                AsyncImage(model = newImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }

            current != null -> {
                RecipeImage(current, ImageSize.Medium, modifier = Modifier.fillMaxSize(), shape = AromaTheme.shapes.cardImage)
            }

            else -> {
                NoPhoto(onPick)
            }
        }
        if (newImage != null || current != null) {
            IconButton(
                icon = R.drawable.ic_add_photo_alternate,
                contentDescription = stringResource(R.string.editor_change_photo),
                onClick = onPick,
                place = IconButtonPlace.Photo,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.s8),
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
            .background(AromaTheme.colors.surface)
            .clickable(role = Role.Button, onClick = onPick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_plate),
            contentDescription = null,
            tint = AromaTheme.colors.inkTertiary,
            modifier = Modifier.size(Glyph.nav),
        )
        Spacer(Modifier.height(Space.s12))
        Text(stringResource(R.string.editor_pick_photo), style = AromaTheme.type.label)
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
    TonalBox(Modifier.padding(horizontal = PageMargin), contentPadding = PaddingValues(vertical = Space.s4)) {
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
                if (index > 0) Separator(start = if (numbered) BoxPadding + StepNumberWidth else BoxPadding)
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
        TextAction(addLabel, addRow, Modifier.padding(horizontal = BoxPadding), icon = R.drawable.ic_add)
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
    val body = AromaTheme.type.body
    // Lines starting with # become section headings; setting them in Medium shows that while typing.
    val style = if (text.trimStart().startsWith("#")) body.copy(fontWeight = FontWeight.Medium) else body
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = BoxPadding, end = if (onRemove != null) Space.s4 else BoxPadding),
        verticalAlignment = Alignment.Top,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            textStyle = style.copy(color = AromaTheme.colors.ink),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (onNext != null) ImeAction.Next else ImeAction.Default,
                ),
            keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
            cursorBrush = SolidColor(AromaTheme.colors.accent),
            decorationBox = { field ->
                // The whole 56 dp row takes the tap, not just the line of text.
                Row(
                    Modifier
                        .heightIn(min = RowHeight.oneLine)
                        .padding(vertical = Space.s16),
                ) {
                    if (number != null) StepNumber(number)
                    Box(
                        Modifier
                            .weight(1f)
                            .alignByBaseline(),
                    ) {
                        if (text.isEmpty()) {
                            Text(placeholder, style = body, color = AromaTheme.colors.inkSecondary)
                        }
                        field()
                    }
                }
            },
        )
        if (onRemove != null) {
            // Level with the first line of text.
            IconButton(R.drawable.ic_close, removeLabel, onRemove, Modifier.padding(top = Space.s4))
        }
    }
}

@Composable
private fun RowScope.StepNumber(number: Int) {
    val label = stringResource(R.string.editor_step, number)
    Text(
        text = number.toString(),
        style = AromaTheme.type.section.tabular(),
        color = AromaTheme.colors.accent,
        modifier =
            Modifier
                .width(StepNumberWidth)
                .alignByBaseline()
                .semantics { contentDescription = label },
    )
}

/** The gutter a step's number stands in, as on the recipe page. */
private val StepNumberWidth = 40.dp

private const val PHOTO_RATIO = 4f / 3f
