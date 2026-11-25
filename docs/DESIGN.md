# Aroma design

A well-set cookbook in two colours: warm paper, ink and one sage, a book face (Newsreader) for
names and recipes, a calm sans (Instrument Sans) for everything that is read or tapped. Ink is for
text, icons and the one main action. Sage marks amounts, links, progress and what is chosen, and
red is kept for errors and for removing something for good. The photos bring all other colour.
Light and dark follow the system; "Colours from your wallpaper" tints only the sage. The widgets
take the same colours, from `widget_colors.xml`, which follows `Color.kt`. Launchers set a widget's
words in the system's serif and sans, as they allow no other fonts.

The values live in `ui/theme` (`Color.kt`, `Type.kt`, `Tokens.kt`, `Shadows.kt`, `Theme.kt`), the
pieces in `ui/components`, one of each. Screens under `feature/` are built from those two only, and
`DesignRulesTest` keeps them to it: colours from the theme, text set with Aroma's `Text` in roles
that hyphenate, controls, shapes and motion from Aroma instead of Material, no outlines, no dividers
but the separator, no FABs, no capitals or letter spacing, text at least 4.5 : 1 and meaningful
shapes at least 3 : 1 against their ground.

## Rules

- **Space.** 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp. Page margin 24, box padding 16, boxes 12
  apart, sections 40 apart, 12 from a section's title to its content. From the status bar to a
  title 16, title to subtitle 4, head to content 24, back button row to title 16, eyebrow to title 8.
  Recipe grid: 2 columns, 16 apart, rows 28 apart. The 28 is the one distance off the scale: a row
  of cards carries its names and times under the photos and needs a little more than 24. Content
  that scrolls ends 24 above any bar, so nothing stays hidden under one. Touch targets at least 48.
  The ingredients on a recipe's page are lines of 40, as a cookbook sets its list: at 48 they read
  like a list of settings. They meet without a gap across the page, so a tap still finds its line.
- **Edges.** Text, boxes, fields and the first pill of a selection row all start on the page
  margin, with nothing reaching past it. The meal plan sets its days on that margin, every row as
  tall as a meal's 64 dp picture; today says "Heute" in sage over its date in full ink. Only the
  week strip on Home puts today's date in an ink circle.
- **Surfaces.** On the page's paper stand boxes in `surface`; inside a box, `surfaceInBox`. No
  borders. A box holds a state, an action or figures, such as today's meal, the week or the
  shopping list. What is read, recipes, ingredients and steps, stands open on the paper, as in a
  cookbook. The one line is `separator`, inset to the text, between rows of a grouped list in a box.
  Bottom bars and sheets cast a soft warm shadow, twice as dense in the dark, and have no line. Only
  what floats over the page casts a shadow, never a box. Photos have a fine outline at 6 % inside
  their edge, so a light one does not melt into the paper. A recipe's photo at the head of its page
  is shaded at the top, so the status bar and the buttons read on it.
- **Bars.** Navigation bar 80 dp (16 above the pill, 12 below the label), labels grow to 130 %.
  Action bars have 16 dp all around their buttons. The collapsed head is 64 dp, so its circles keep
  12 dp of air above and below and never crowd its foot.
- **Icons.** Aroma draws its own on a 24 dp grid, in 1.5 dp strokes with round ends, and a new one
  is drawn the same way, so they stay one set. A chosen tab's icon has 2 dp strokes. An icon that
  stands alone and takes a tap stands on a circle, so it reads as a button: 40 dp on the page, on
  photos and in the heads, 48 in the bottom bars and 56 beside cook mode's taller buttons, always in
  a 48 dp target. The heads keep 40, with 12 dp of air above and below in the 64 dp collapsed head,
  and the back button keeps one size and place as the head collapses: over a recipe's photo, its
  buttons stand in the row the collapsed head will have. Icons in a button with words, a row or a
  line of meta have no circle of their own.
- **Actions.** A screen has at most one primary button, in ink, for its main action. A screen
  without one, such as the plan or the shopping list, has none rather than one made up for it. What
  manages a thing rather than uses it waits a level down, in the ⋯ menu, a sheet or the editor, so
  pages stay calm and nothing is lost. On a sheet of choices, the way to undo them all, such as
  resetting the filter, stands at the end of its title while there is anything to undo.
- **States.** Emptiness is quiet: one line and its action, no picture, never taller than what it
  stands for. Where the two do not fit on one line, the action moves under the words instead of
  squeezing them. A page that cannot load says so the same way, with "Try again". An error of a
  field or a button stands under it, where it happened. What loads shows a skeleton in the shape of
  what is coming. The only spinner turns in a button, or in its place, while its action runs. What
  a recipe does not have is left out, not shown empty: empty stars would read as a poor rating, and
  "0 min" as a dish that takes no time. A recipe without a photo opens like any pushed page.
- **Type.** Only the roles in `Type.kt`. The serif names: page titles, recipe names, section titles,
  group labels, figures. Everything else is the sans. Titles hyphenate and break balanced; words of
  four to seven letters are never hyphenated. A compound breaks after a hyphen, never before one,
  and each part hyphenates on its own: by itself, Android never hyphenates a word that holds a
  hyphen and would break it anywhere. Text that is cut ends after a whole word with "…", not in the
  middle of one.
- **Numbers.** Set as a typesetter sets them. Amounts, times and counts have tabular figures, so
  they stand in columns and do not jitter. A narrow no-break space holds a number to its unit,
  "500 g", so the two never part at a line's end. A span takes an en dash, "10–15 min", and a
  fraction its own character, "½". The dot between facts keeps to the word before it, so no line
  starts with "·". The narrow space is for the screen alone: times written back to Mealie keep
  plain spaces.
- **Motion.** 150 ms for edges, 250 ms for a change of state and the selection pill, 220 ms for a
  page: the old page fades out, the new one fades in and drifts a few dp into place. The back
  gesture plays the same under the finger, and the tab bar comes and goes with its page. Nothing
  zooms, grows or travels between screens. Sheets follow the back gesture down. A sheet's shadow
  goes with it and fades as it leaves, so the page is clear the moment the sheet has gone. Menus,
  snackbars and dialogs fade in and drift 8 dp into place, and Material's parts settle on springs
  that never overshoot. Content scrolls into a soft 16 dp edge under the status bar, and the
  collapsed head fades in with its paper. A skeleton shows only after 150 ms and fades out as the
  content fades in. A new look dissolves in 250 ms. Cards and boxes give way to 0.98 when pressed
  and tint with ink at 6 %, and a quick tap shows the whole press. With the system's animations
  off, pages only cross-fade.
- **Motion in a page.** A change happens where it is. A figure that changes rolls 40 % of its line,
  up as it counts up and down as it counts down, and the words around it stand still. A label that
  changes in its place fades out before the new one fades in. A row that comes fades in where it
  lands while the rows after it glide aside, or, outside a list, opens its room as it fades in. A row
  that goes fades, then gives its room up. A tick fades with its circle. A switch's knob glides and
  its track takes on its colour. A row that scrolls sideways fades out over the margin where more of
  it lies beyond. A recipe's photo slides away at half the page's pace. A week swiped sideways
  follows the finger, held back, and springs back where letting go would not turn it. A timer's dial
  runs down with the clock, the one thing that keeps an even pace. The phone gives a tap where
  something is done: a tick, a switch, a step reached, a timer started, a dish planned.
- **Access.** Everything that takes a tap has words or a description for TalkBack; the screenshot
  tool checks it on every screen it renders. Up to 130 % font size, Android's largest before
  version 14, text reads in full; rows move an action under their words where the column would get
  too narrow. Larger sizes, up to 200 %, must not break anything, but a long word may end in "…".

## Writing

For every text people read: the README, the changelog, release notes, commit messages, pull
requests and the app's own words.

- **Facts.** Every claim is checked in the code first, not taken from memory or the changelog.
- **Content.** What Aroma does for the user, not how it works. No numbers, no lists of examples, no
  technical detail. Only what Aroma adds: what Mealie does by itself is neither listed as Aroma's
  nor explained. As of Mealie 3.31 that is scaling, the meal planner (also adding a week to a
  list), shopping lists sorted by aisle, a cook view that keeps the screen on, importing links (also
  through the share menu of its web app), AI imports from text and photos, categories, tags,
  cookbooks, a random recipe and app shortcuts.
- **Tone.** Calm, modest and plain, for readers who aren't technical. Short sentences and common
  words. No sales tone, no lists whose every line starts with a verb, no ", with X and Y" tacked
  onto a line, no filler such as "right in", "seamless" or "only ever", no dashes or semicolons
  where two sentences would do, and no jokes that need explaining.
- **Length.** As short as it can be. In the README, details go into toggles, and only if a reader
  needs them. The owner's own sentences stay as they are.
- **Review.** Before a text is shown, every line is checked: is it about Aroma, is it true, does a
  reader need it? Then the whole text is read again. It is shown in full before it is merged.
- **Commits and pull requests.** Commit messages are short plain English in the imperative and say
  what changes, as in `git log`. No prefixes, no trailers, no tool signatures such as "Generated
  with". Pull request descriptions are in English and at most 300 characters.

## Checking a change

`scripts/check.sh` runs what CI runs: formatting, lint, the tests and the APK, in about a minute.
Screenshots of every screen, sheet and widget render on demand for a design review, with
`./gradlew :app:recordRoborazziDebug -Pscreenshots --tests 'app.aroma.screenshots.*'` or the
Screenshots workflow; the images land in `app/build/outputs/roborazzi`. Each screen shows in
German, light and dark, in English, and at 130 % font size on a 360 dp phone, with the sample
content and with German edge cases, such as long compound titles and a recipe with nothing but its
name. The tool lives in `app/src/screenshots` and builds only with `-Pscreenshots`.
