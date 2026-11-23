# Aroma design

A cookbook on warm paper: ink for words, photos for colour, one sage green for what you act on
now. Soft paper cards hold what belongs together; type and space do the rest. Every choice can
be explained in one sentence about calm, clarity or comfort.

The code holds the values: `ui/theme` the tokens (`Tokens.kt`, `Theme.kt`, `Color.kt`, `Type.kt`,
`Shadows.kt`) and `ui/components` the components. Screens under `feature/` use tokens and
components only. `DesignRulesTest` checks the rules marked *(checked)* on every build.

## Principles

1. **Paper and ink.** Colour comes from the photos; sage marks the one action of a screen, the
   selection and today. Nothing else is coloured.
2. **One keyline.** Every title, sentence, photo and control starts on the 24 dp margin.
3. **Groups are visible.** Rows that belong together sit on a soft card; groups are set apart by
   space and named by a kicker or a serif heading above them.
4. **Type before boxes.** Hierarchy comes from size, typeface and colour; a card groups, it never
   decorates.
5. **One focal point.** Each screen has at most one filled button.
6. **Calm motion.** Things fade and settle; nothing bounces, blinks or wears a badge.
7. **Removal test.** If nobody would miss an element, it goes.

## Tokens

| Token | Values | Use |
|---|---|---|
| `Space` | xxs 4, xs 8, s 12, m 16, l 24, xl 32, xxl 48, xxxl 64 | Every distance in a layout |
| `ScreenMargin` | 24 | The keyline on both sides |
| `CardInset` | 12 | How far a soft card stands outside the keyline |
| `IconKeyline` | 64 | Text after a 24 dp glyph in a row |
| `Glyph` | s 16, m 20, l 24, xl 32 | Line glyphs beside small text and links, in rows and fields, in bars, standing alone |
| `PageEndSpace` | 32 | Free space after the last item of a scrolling page |
| Shapes | extraSmall 4, small 8, medium 12, large 16, extraLarge 24; circle | Placeholder lines, thumbnails, photos and fields, cards, sheets; buttons, chips, avatars |
| `Lines` | hairline 0.5, control 1, checkRing 1.25 | Between rows, around controls, a check's ring |
| `Motion` | PRESS 120, STATE 180, EXPAND 240, PULSE 1000, with `Settle` | Pressing, changing state, moving and resizing; a pulse while something waits |

Colours, by role (`MaterialTheme.colorScheme` and `extras`):

| Role | Use |
|---|---|
| `background` | The paper of every page |
| `onSurface` / `onSurfaceVariant` | Ink / soft ink for second lines, kickers and icons |
| `primary` | Sage: the selection, today, links in a cooking step |
| `extras.cta` / `extras.onCta` | The one filled button of a screen |
| `extras.card` | Soft paper under grouped rows |
| `extras.cardHighlight` | The sage-washed card: today, and questions to the user |
| `extras.raised` | Floating layers: the tab bar, sheets, menus, the thumb of a segmented control |
| `extras.hairlineStrong` | Borders of controls |
| `error` | Removing and failing, nothing else |

Type (`Type.kt`): Newsreader for display and titles (`displaySmall` page titles,
`headlineSmall` section and sheet titles, `CardTitle` titles in lists and cards, `Standfirst`
leads, `CookStep` steps in cook mode, `StepNumber` step numbers, `TimerDigits` timers); Figtree
for running text (`body*`, `Reading`, `ShoppingModeItem` items in shopping mode), labels
(`label*`) and kickers (`KickerStyle`, tracked caps). Numbers that line up use `tabular()`.

## Rules

1. **Tokens only.** Screens use `Space`, the margins and the theme. A size that is not spacing is a
   named constant in its file, never a bare number in a layout *(checked: no `N.dp` in `feature/`
   outside a named constant)*.
2. **Colours from the theme.** No colour literals outside `ui/theme` *(checked)*.
3. **Shapes from the theme.** No `RoundedCornerShape` outside `ui/theme` and `ui/components`
   *(checked)*.
4. **Type from `Type.kt`.** No font sizes at call sites *(checked)*.
5. **Components, not primitives.** Screens use Aroma's components; Material's buttons, cards,
   dialogs, sheets, menus, switches, chips, fields, snackbars and progress indicators appear only
   inside `ui/components` *(checked)*.
6. **Motion from `Motion`.** No literal durations; movement and size changes settle with
   `Motion.Settle` *(checked: no `tween(N)`)*.
7. **Cards.** Rows that belong together sit on a `SoftCard`: a settings group, a day of the plan,
   a section of ingredients or of the shopping list, a day of the timeline, the times of a recipe.
   Hairlines separate rows only inside a card. A card's content keeps the 24 dp margin; the card
   stands 12 dp outside it, with 16 dp corners, no border and no shadow. In lazy lists one card
   spans several items through `CardPart`. Photos, prose and page headers never sit on cards.
8. **Highlight.** `highlight = true` is for today and for a question to the user (how did it
   taste?), at most one per screen.
9. **One filled button.** A `PrimaryButton` per screen at most; everything else is a
   `SecondaryButton`, a `QuietLink` or an icon action.
10. **Headers.** Tab pages start with `PageHeader`; pushed pages with `DetailTopBar` over a
    `LargeTitle`; sections with `SectionHeader` (serif) or a `Kicker` above a card.
11. **Sheets.** `AromaBottomSheet` for every sheet; rows of actions are `SheetAction`; an action
    that changes the page underneath runs after `hideThen`.
12. **Progress.** `InlineProgress` for work in place, `Placeholder` shapes while a page loads,
    `PrimaryButton(loading = true)` while its own action runs.
13. **Touch.** Every target is at least 48 dp; the glyph, not its target, stands on the keyline.
14. **Words.** Strings live in `values` and `values-de`, in step; text is never concatenated in
    code where a formatted string can say it.
15. **Accessibility.** Page, section and sheet titles are headings; every control without words
    has a description; live regions announce changes of state, not progress ticks.

## Components

| Component | For |
|---|---|
| `PageHeader`, `DetailTopBar`, `LargeTitle`, `SectionHeader`, `Kicker` | Headings |
| `SoftCard`, `CardPart`, `Hairline` | Groups and the lines inside them |
| `PrimaryButton`, `SecondaryButton`, `QuietLink`, `HeaderAction`, `IconAction`, `HairlineIconButton`, `OnPhotoButton` | Actions |
| `SwitchRow`, `SegmentedControl`, `AromaFilterChip`, `Stepper`, `RatingStars`, `CheckCircle` | Choices |
| `LabeledTextField`, `LabeledSecureTextField`, `PillTextField` | Input |
| `AromaBottomSheet`, `SheetAction`, `AromaAlertDialog`, `AromaDropdownMenu`, `AromaMenuItem` | Layers |
| `PhotoCard`, `RecipeCard`, `RecipeListItem`, `RecipeImage`, `Avatar`, `IngredientList` | Content |
| `InlineProgress`, `Placeholder`, `PlaceholderLines`, `MessageState`, `ErrorState`, `OfflineNotice` | States |
