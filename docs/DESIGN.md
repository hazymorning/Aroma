# Aroma design

A well-set cookbook in two colours: warm paper, ink and one sage, a book face (Newsreader) for
names and recipes, a calm sans (Instrument Sans) for everything that is read or tapped. The photos
bring the colour. Light and dark follow the system; "Colours from your wallpaper" tints only the
sage.

The values live in `ui/theme` (`Color.kt`, `Type.kt`, `Tokens.kt`, `Shadows.kt`, `Theme.kt`), the
pieces in `ui/components`, one of each. Screens under `feature/` are built from those two only, and
`DesignRulesTest` keeps them to it: sizes from tokens or named constants, spacing on the scale,
colours from the theme, no outlines, no dividers but the separator, no FABs, no capitals or letter
spacing, text at least 4.5 : 1 and meaningful shapes at least 3 : 1 against their ground.

## Rules

- **Space.** 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp. Page margin 24, box padding 16, boxes 12
  apart, sections 40 apart, 12 from a section's title to its content. From the status bar to a
  title 16, title to subtitle 4, head to content 24, back button row to title 16, eyebrow to title 8.
  Recipe grid: 2 columns, 16 apart, rows 28 apart. Touch targets at least 48.
- **Edges.** Text, boxes, fields and the first pill of a selection row all start on the page
  margin, with nothing reaching past it. Today is marked by a dark circle behind its date, on Home
  and in the meal plan alike.
- **Surfaces.** On the page's paper stand boxes in `surface`; inside a box, `surfaceInBox`. No
  borders. The one line is `separator`, inset to the text, between rows of a grouped list in a box.
  Bottom bars and sheets cast a soft warm shadow, twice as dense in the dark, and have no line.
- **Bars.** Navigation bar 80 dp (16 above the pill, 12 below the label), labels grow to 130 %.
  Action bars have 16 dp all around their buttons. The collapsed head is 56 dp.
- **Type.** Only the roles in `Type.kt`. The serif names: page titles, recipe names, section titles,
  group labels, figures. Everything else is the sans. Titles hyphenate and break balanced; words of
  four to seven letters are never hyphenated.
- **Motion.** 150 ms for edges, 250 ms for a change of state and the selection pill, 220 ms for a
  page: the old page fades out, the new one fades in and drifts a few dp into place. Nothing zooms,
  grows or travels between screens. Cards and boxes give way to 0.98 when pressed and tint with ink
  at 6 %. With the system's animations off, pages only cross-fade.
- **Access.** Everything that takes a tap has words or a description for TalkBack; the screenshot
  tests check it. At 200 % font size nothing is cut off; rows move an action under their words
  where the column would get too narrow.

## Checking a change

`scripts/check.sh` runs what CI runs: formatting, lint, the tests and the APK, in about a minute.
Screenshots of every screen, sheet and widget render on demand for a design review, with
`./gradlew :app:recordRoborazziDebug -Pscreenshots --tests 'app.aroma.screenshots.*'` or the
Screenshots workflow; the images land in `app/build/outputs/roborazzi`.

The design round that set these rules is written up in `docs/design`: the brief (`AUFTRAG.md`),
every decision and deviation (`PROGRESS.md`) and the report with before and after images
(`BERICHT.md`).
