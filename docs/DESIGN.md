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
3. **One accent.** Sage marks the one filled action of a screen, what is chosen (a chip, a tab,
   a check, a switch, today), links (every link is sage), group labels and step numbers, amounts in
   ingredient and shopping lists and those written into steps, and the brand: the wordmark. It is
   never decoration. A muted sage, soft fill and dark sage words, lifts the few actions that come
   right after the main one: plan and shop on a recipe, and nothing else yet. It stays that rare, or
   it stops lifting anything.
4. **Two typefaces, seven sizes.** Newsreader names things; Figtree carries everything that is
   read or tapped. Nothing is set in capitals or tracked out.
5. **One controls style.** Every control is a soft pill on the card fill; the one main action is
   a sage pill; the actions right after it take the muted accent; the chosen value of a chip or
   segment takes the sage wash.

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
groups inside a section sit `Space.l` apart; a caption sits `Space.xs` under its photo; a message
alone on a page stands a third of the way down the room below the page's head. Screens size with
tokens or named constants only *(checked)*.

## Colour

| Role | Light | Dark | For |
|---|---|---|---|
| `background` | #F8F8F3 | #121411 | The page |
| `extras.card` | #EEEFE7 | #1E211D | Cards, fields, chips, quiet buttons |
| `extras.selected` / `onSelected` | #DCE7D4 | #2E3D2B | The chosen chip, segment, today |
| `extras.cta` / `onCta` | #486447 | #3A5638 | The one filled action |
| `extras.mutedAccent` / `onMutedAccent` | #D8E3CF / #2F4B2C | #263324 / #C3DCB8 | The actions right after the main one: plan and shop on a recipe (`SecondaryButton(accented = true)`) |
| `extras.raised` | #FFFFFF | #2C2F2A | Sheets, menus; the tab bar, a step darker in the dark |
| `primary` | #486447 | #AECDA5 | Links, checks, group labels, step numbers, amounts, the brand |
| `onSurface` / `onSurfaceVariant` | ink / soft ink | | Text and glyphs |
| `error` | | | Removing and failing, nothing else |

No colour literals outside `ui/theme` *(checked)*. Words on the muted accent stand out from it at
least 4.5:1 *(checked)*. With "Colors from your wallpaper", the same roles come from Material You.

## Shape and depth

Photos and cards 16 dp, thumbnails 12 dp, sheets and dialogs 28 dp, controls fully round; shapes come
from the theme *(checked)*. The page is flat. Only what floats over it casts the one soft shadow: the
tab bar, the floating actions (cook, add a recipe), buttons on a photo, menus, sheets and the
snackbar. Nothing on the page is outlined *(checked)*; a field shows a sage ring only while it has the
cursor.

The tab bar is a sheet of paper across the bottom edge, not a floating pill: it runs from side to
side and down over the system navigation, and its shadow falls up onto the page scrolling below it.
Its four tabs stand far apart, with 20 dp of air above the glyphs and below the labels.

## Pieces

| Need | Component |
|---|---|
| Page head | `PageHeader` (tab pages), `DetailPage` / `DetailTopBar` / `LargeTitle` (pushed pages) |
| Section | `SectionHeader` (with an optional link to more) |
| Group of rows | `CardGroup` = `GroupLabel` over a `SoftCard`; `CardPart` across lazy items; `Hairline` |
| A day (plan, timeline) | `DayHeader`, at the head of the day's card |
| Views of a page | `PageTabs` |
| Filters | `AromaFilterChip` |
| A choice of two or three in a form | `SegmentedControl` |
| Actions | `PrimaryButton` (one per screen), `FloatingAction` (that one action over a long page: cooking a recipe, adding one to the library), `SecondaryButton`, `QuietLink`, `IconAction`, `HeaderAction`, `TonalIconButton`, `OnPhotoButton` |
| Values | `Stepper`, `SwitchRow`, `RatingStars`, `CheckCircle` |
| Input | `LabeledTextField`, `LabeledSecureTextField`, `PillTextField`, `SearchPill` |
| Layers | `AromaBottomSheet`, `SheetAction`, `SheetFooter`, `AromaDropdownMenu`, `AromaAlertDialog` |
| Content | `RecipeCard`, `PhotoCard`, `RecipeListItem`, `RecipeImage`, `IngredientList`, `Avatar` |
| States | `MessageState`, `ErrorState`, `OfflineNotice`, `Placeholder`, `InlineProgress` |

Screens use these, not Material's buttons, cards, sheets, menus, chips, switches, fields or
dialogs *(checked)*.

## Motion

Motion is quick and quiet: things fade where they stand and move without a jump; nothing bounces,
overshoots or hops into place. Durations come from `Motion` *(checked)*: 120 ms for a press and for
what leaves, 180 ms for a change of state and for what arrives, 240 ms for moving and resizing. A
page changes in 300 ms: the old page's fade, then the new one's.

- **Two curves.** What moves or changes size in sight glides (`Motion.Glide`, a tween's own curve):
  it starts easy, so no frame jumps ahead. What arrives settles (`Motion.Settle`): its quick start
  passes while it is still faint. A page's fades run evenly.
- **Let go.** What a finger drags and lets go, a swiped row or the panel in cook mode, carries on
  at the finger's speed on `Motion.LetGo`, a spring that never bounces, and stops where it lands.
- **Coming and going.** Things fade in and out where they stand, with no more than a small drift or
  scale. A floating action is there with its page from the first frame; it folds to its glyph
  while the page scrolls on and unfolds when it heads back.
- **Lists.** Rows have stable keys and move with `animateRow` *(checked)*: a new row fades in as the
  rows after it glide aside; a leaving row holds still while they glide over it, so a card never
  shows a gap. Nothing in a list grows in steps *(checked)*: a row that changes size does so at
  once, and the list glides around it. A row that changes groups fades out and in rather than
  sliding across the list; another week, list or mode is a new page, not an edit of the old one.
  A row swiped away lets go like that, and the list closes up as soon as it has all but left.
- **Pages.** The old page fades out as it starts to drift; the new one fades in as it drifts the
  rest of the way. Tabs only fade. The tab bar comes and goes with its page.
- **Cheap frames.** Opacity, offsets and sizes are read where they are drawn or placed
  (`graphicsLayer { }`, `offset { }`, a layout's measure), so a running animation recomposes nothing.
- **No animations.** With animations turned off in Android, everything lands at once; what Compose
  does not time itself, a photo's crossfade or the stars' stagger, follows the same setting.

## Words and access

Strings live in `values` and `values-de`, in step. Page, section, group and sheet titles are
headings; every control without words has a description; targets are at least 48 dp.

## How changes are checked

A change to a screen is judged with real photos, in light and dark, in English and German, next to
the screens it sits between, and against how it looked before. A screen that fits the rules but not
its neighbours is not done.
