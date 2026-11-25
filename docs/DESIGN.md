# Aroma design

A well-set cookbook in two colours: warm paper, ink and one sage, a book face (Newsreader) for
names and recipes, a calm sans (Instrument Sans) for everything that is read or tapped. The photos
bring the colour. Light and dark follow the system; "Colours from your wallpaper" tints only the
sage.

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
  Recipe grid: 2 columns, 16 apart, rows 28 apart. Touch targets at least 48.
- **Edges.** Text, boxes, fields and the first pill of a selection row all start on the page
  margin, with nothing reaching past it. The meal plan sets its days on that margin, every row as
  tall as a meal's 64 dp picture; today says "Heute" in sage over its date in full ink. Only the
  week strip on Home puts today's date in an ink circle.
- **Surfaces.** On the page's paper stand boxes in `surface`; inside a box, `surfaceInBox`. No
  borders. The one line is `separator`, inset to the text, between rows of a grouped list in a box.
  Bottom bars and sheets cast a soft warm shadow, twice as dense in the dark, and have no line.
- **Bars.** Navigation bar 80 dp (16 above the pill, 12 below the label), labels grow to 130 %.
  Action bars have 16 dp all around their buttons. The collapsed head is 56 dp.
- **Type.** Only the roles in `Type.kt`. The serif names: page titles, recipe names, section titles,
  group labels, figures. Everything else is the sans. Titles hyphenate and break balanced; words of
  four to seven letters are never hyphenated.
- **Motion.** 150 ms for edges, 250 ms for a change of state and the selection pill, 220 ms for a
  page: the old page fades out, the new one fades in and drifts a few dp into place. The back
  gesture plays the same under the finger, and the tab bar comes and goes with its page. Nothing
  zooms, grows or travels between screens. Sheets follow the back gesture down. Menus, snackbars
  and dialogs fade in and drift 8 dp into place, and Material's parts settle on springs that never
  overshoot. Content scrolls into a soft 16 dp edge under the status bar, and the collapsed head
  fades in with its paper. A skeleton shows only after 150 ms and fades out as the content fades
  in. A new look dissolves in 250 ms. Cards and boxes give way to 0.98 when pressed and tint with
  ink at 6 %. With the system's animations off, pages only cross-fade.
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
content and the brief's test texts. The tool lives in `app/src/screenshots` and builds only with
`-Pscreenshots`.

The design round that set these rules is written up in `docs/design`: the brief (`AUFTRAG.md`),
every decision and deviation (`PROGRESS.md`) and the report with before and after images
(`BERICHT.md`).
