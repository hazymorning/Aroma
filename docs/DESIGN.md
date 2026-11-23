# Aroma design

One look, everywhere: a calm page of warm paper, soft cards for everything that belongs together,
one sage green for what is chosen or acted on, a serif for names and a sans for everything else.
The photos bring the colour.

The values live in `ui/theme` (`Tokens.kt`, `Type.kt`, `Theme.kt`, `Color.kt`, `Shadows.kt`), the
pieces in `ui/components`. Screens under `feature/` are built from those two only.
`DesignRulesTest` checks every rule marked *(checked)*.

## The five decisions

1. **One edge.** Everything on a page stands on the 16 dp margin: titles, photos, fields,
   buttons, chips and the edges of cards. Text inside a card stands 16 dp further in, and the
   label that names a card stands level with that text.
2. **One container.** The soft card (`SoftCard`): the paper's fill one step darker, 16 dp corners,
   no border, no shadow. Rows that belong together sit on one card, separated by hairlines inset
   to their text. Nothing is outlined, and a card never holds another card.
3. **One accent.** Sage marks only the filled main action of a screen, what is chosen (a chip,
   a tab, a check, a switch, today) and links. It is never decoration.
4. **Two typefaces, seven sizes.** Newsreader names things; Figtree carries everything that is
   read or tapped. Nothing is set in capitals or tracked out.
5. **One controls style.** Every control is a soft pill on the card fill; the one main action is
   a sage pill; the chosen value of a chip or segment takes the sage wash.

## Type

| Role (`AromaType`) | Face | sp | For |
|---|---|---|---|
| `PageTitle` | Newsreader Display | 28 / 34 | Page titles, a recipe's name on its page |
| `SectionTitle` | Newsreader Text Medium | 22 / 28 | Sections, days of the plan, sheets, dialogs |
| `Name` | Newsreader Text Medium | 18 / 24 | Recipe names on cards and in rows |
| `Reading` | Figtree | 24 / 34 | The step in cook mode |
| `Timer` | Figtree Medium, tabular | 28 / 34 | A running timer |
| `Large` | Figtree | 22 / 28 | Items in shopping mode |
| `Body` / `BodyStrong` | Figtree / Medium | 16 / 24 | Running text, rows; amounts and values |
| `Small` | Figtree | 14 / 20 | Second lines, notes, meta ("25 min · 4.5") |
| `Label` | Figtree Medium | 14 / 20 | Buttons, chips, tabs, group labels |
| `Caption` | Figtree Medium | 12 / 16 | Tab bar labels, the "Today" badge |

Material's type slots map onto these roles, so Material's own components stay on the scale.
Screens use the roles, never Material's slots *(checked)*, never a font size *(checked)*, never
capitals *(checked)* and never letter spacing *(checked)*. The scale has exactly these seven
sizes *(checked)*.

## Space

| Token | dp | For |
|---|---|---|
| `ScreenMargin` | 16 | The page's edge, left and right |
| `CardPadding` | 16 | Content inside a card |
| `ContentEdge` | 32 | Text inside a card, and the label above it |
| `SectionSpace` | 32 | Above a section's title |
| `Space` | 4, 8, 12, 16, 24, 32, 48 | Every other distance |
| `RowHeight` | 56, 72 | Rows of one line; of two lines or with a thumbnail |
| `ControlHeight` | 52 | Buttons and fields that span a column |
| `ChipHeight` | 36 | Chips and compact buttons |
| `TouchTarget` | 48 | The least size of anything that takes a tap |

Rhythm: a section starts `SectionSpace` below the last one, its content `Space.xs` below its title;
groups inside a section sit `Space.l` apart; a caption sits `Space.xs` under its photo. Screens size
with tokens or named constants only *(checked)*.

## Colour

| Role | Light | Dark | For |
|---|---|---|---|
| `background` | #F8F8F3 | #121411 | The page |
| `extras.card` | #EEEFE7 | #1E211D | Cards, fields, chips, quiet buttons |
| `extras.selected` / `onSelected` | #DCE7D4 | #2E3D2B | The chosen chip, segment, today |
| `extras.cta` / `onCta` | #486447 | #3A5638 | The one filled action |
| `extras.raised` | #FFFFFF | #242722 | The tab bar, sheets, menus |
| `primary` | #486447 | #AECDA5 | Links, checks, group labels, step numbers |
| `onSurface` / `onSurfaceVariant` | ink / soft ink | | Text and glyphs |
| `error` | | | Removing and failing, nothing else |

No colour literals outside `ui/theme` *(checked)*. With "Colors from your wallpaper", the same roles
come from Material You.

## Shape and depth

Photos and cards 16 dp, thumbnails 12 dp, sheets and dialogs 28 dp, controls fully round; shapes come
from the theme *(checked)*. The page is flat. Only what floats over it casts the one soft shadow: the
tab bar, the cook button, buttons on a photo, menus, sheets and the snackbar. Nothing on the page is
outlined *(checked)*; a field shows a sage ring only while it has the cursor.

## Pieces

| Need | Component |
|---|---|
| Page head | `PageHeader` (tab pages), `DetailPage` / `DetailTopBar` / `LargeTitle` (pushed pages) |
| Section | `SectionHeader` (with an optional link to more) |
| Group of rows | `CardGroup` = `GroupLabel` over a `SoftCard`; `CardPart` across lazy items; `Hairline` |
| Views of a page | `PageTabs` |
| Filters | `AromaFilterChip` |
| A choice of two or three in a form | `SegmentedControl` |
| Actions | `PrimaryButton` (one per screen), `SecondaryButton`, `QuietLink`, `IconAction`, `HeaderAction`, `TonalIconButton`, `OnPhotoButton` |
| Values | `Stepper`, `SwitchRow`, `RatingStars`, `CheckCircle` |
| Input | `LabeledTextField`, `LabeledSecureTextField`, `PillTextField`, `SearchPill` |
| Layers | `AromaBottomSheet`, `SheetAction`, `SheetFooter`, `AromaDropdownMenu`, `AromaAlertDialog` |
| Content | `RecipeCard`, `PhotoCard`, `RecipeListItem`, `RecipeImage`, `IngredientList`, `Avatar` |
| States | `MessageState`, `ErrorState`, `OfflineNotice`, `Placeholder`, `InlineProgress` |

Screens use these, not Material's buttons, cards, sheets, menus, chips, switches, fields or
dialogs *(checked)*.

## Motion

Things fade and settle (`Motion.Settle`); nothing bounces. 120 ms for a press, 180 ms for a change
of state, 240 ms for moving and resizing; durations come from `Motion` *(checked)*.

## Words and access

Strings live in `values` and `values-de`, in step. Page, section, group and sheet titles are
headings; every control without words has a description; targets are at least 48 dp.

## How changes are checked

A change to a screen is judged with real photos, in light and dark, in English and German, next to
the screens it sits between, and against how it looked before. A screen that fits the rules but not
its neighbours is not done.
