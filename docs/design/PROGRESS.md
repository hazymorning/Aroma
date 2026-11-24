# Design-Runde „Kochbuch" – Arbeitsstand

Gedächtnis dieser Runde. Auftrag: [`AUFTRAG.md`](AUFTRAG.md). Branch: `design/kochbuch`.
Wird nach jedem Schritt aktualisiert.

## Phasen

- [x] P0 Bestandsaufnahme, Vorher-Screenshots (`screenshots/vorher/`, 308 Bilder)
- [x] P1 Fehler beheben (Abschnitt 8)
- [x] P2 Grundlagen (4) und Komponenten (5), alte Varianten migriert und gelöscht
- [ ] P3 Rezeptseite
- [ ] P4 Start und Wochenplan
- [ ] P5 Rezepte, Kochmodus, Einkauf, Einstellungen, übrige Screens, Sheets, Dialoge, Zustände
- [ ] P6 Dark Mode, Bewegung, Haptik, Barrierefreiheit
- [ ] P7 Art-Director-Review, `BERICHT.md`
- [ ] P8 Release-Draft

## Inventar (Stand vor der Runde)

### Stack

- Kotlin 2.4.21, Jetpack Compose (BOM 2026.11.01), Material 3, Navigation Compose 2.10 mit
  typsicheren Routen, Room (Offline-Kopie als JSON der DTOs), Retrofit/OkHttp, Coil 3, Glance-Widgets,
  WorkManager.
- AGP 9.5.2, Gradle 9.9.2, compileSdk 37.2, minSdk 26, targetSdk 36, Version 0.6.0 (versionCode 8).
- Prüfungen: Spotless (ktlint + Compose-Regeln), Android Lint mit `warningsAsErrors` und
  Slack-Compose-Lint, Unit-Tests mit Robolectric, Screenshots mit Roborazzi
  (`recordRoborazziDebug`), `DesignRulesTest` liest Regeln aus dem Code.
- CI: `.github/workflows/ci.yml` (Push auf `main`, jeder PR), `release.yml` (manuell, nur `main`,
  veröffentlicht direkt mit `--latest`), `prune-releases.yml` (manuell).

### Theme (`ui/theme`)

- `Color.kt`: unveränderte Material-3-Rollen (hell #F8F8F3 Papier, Salbei #486447), dazu
  `AromaExtras` in `Theme.kt`: `card` #EEEFE7, `selected` #DCE7D4, `raised` #FFFFFF (Leisten,
  Sheets, Menüs), `cta` #486447, `mutedAccent` #D8E3CF, `shadowInk`, `floatingEdge` (Haarlinie).
- `Type.kt`: `AromaType` mit sieben Größen (28/22/18/24/16/14/12), Titel mit `Hyphens.None`,
  Fließtext mit `Hyphens.Auto`, kein `localeList`.
- `Tokens.kt`: `Space` 4–48, Seitenrand 16, `ControlHeight` 52, `ChipHeight` 36, `RowHeight` 56/72,
  `Glyph` 16/20/24/32, `Motion` 120/180/240 ms, Haarlinien 0,5 dp.
- `Shadows.kt`: `floatingShadow` (FAB, Snackbar), `barShadow` (Tab-Leiste, grün-schwarz getönt),
  `photoButtonShadow`.
- Material You per Einstellung, Standard aus; eingeschaltet färbt es alle Rollen.

### Schriften

- Serif: Newsreader als statische, beschnittene Instanzen (Display Regular, Text Regular, Text
  Medium). Kein Kursivschnitt in der App, obwohl Newsreader einen echten hat.
- Sans: Figtree Regular und Medium.

### Icons

Eigene, einheitliche Linienfamilie: 24-dp-Raster, Strich 1,5 dp, runde Enden und Ecken. Dazu
„_selected"-Varianten der Tabs als Duoton. → bleibt (Abschnitt 4.6: „die bestehende, falls sie
einheitlich ist"). Fehlende Motive (Besteck, Flamme, Uhr, Lesezeichen, Regler, Blatt, Pfeil nach
rechts oben) werden im selben Stil ergänzt.

### Komponenten (`ui/components`, vorher)

| Bereich | Komponenten |
|---|---|
| Köpfe | `PageHeader`, `HeaderAction`, `DetailTopBar` (mit Haarlinie), `DetailPage`, `LargeTitle`, `StatusBarScrim` (Verlauf) |
| Gruppen | `SoftCard` mit `CardPart`, `CardGroup`, `GroupLabel`, `Hairline`, `SectionHeader`, `DayHeader` mit „Heute"-Badge |
| Aktionen | `PrimaryButton` (Salbei, 52 dp), `FloatingAction` (FAB), `SecondaryButton` (drei Varianten: normal, `onCard`, `accented` in Mint), `QuietLink`, `IconAction` (ohne Fläche), `HeaderAction`, `TonalIconButton`, `OnPhotoButton` |
| Auswahl | `AromaFilterChip`, `MenuChip` (Dropdown-Chip), `SegmentedControl`, `PageTabs`, `RatingStars`, `Stepper` (48 dp), `AromaSwitch`, `SwitchRow`, `CheckCircle` |
| Eingabe | `LabeledTextField`, `LabeledSecureTextField`, `PillTextField`, `SearchPill` |
| Ebenen | `AromaBottomSheet`, `SheetAction`, `SheetFooter`, `AromaDropdownMenu`, `AromaMenuItem`, `AromaAlertDialog`, `AromaSnackbarHost`, `AromaPullToRefreshBox` |
| Inhalt | `RecipeCard`/`PhotoCard` (Bild 1:1 oder 4:5, Name 2 Zeilen), `RecipeListItem`, `RecipeMeta` (Zeit · ★ Bewertung, hält leere Höhe), `RecipeImage`, `IngredientList` (Karten mit Haarlinien), `Avatar` |
| Zustände | `MessageState` (Icon, Serif-Titel, Text, Button), `ErrorState`, `OfflineNotice`, `Placeholder`, `PlaceholderLines`, `InlineProgress` |
| Sonstiges | `SwipeToRemove`, `QuietPress`/`PhotoPress` (Abdunkeln), `animateRow`, `fullBleed`, `rememberRichText`/`rememberStepText` |

Allein die Rezeptseite nutzt sieben Stile: Foto-Kreise, Mint-Buttons, Chips, Dropdown-Chip,
Stepper, FAB, Haarlinien-Karten.

### Screens, Sheets, Dialoge, Zustände (vorher)

1. **Anmeldung**: Server-Schritt, Zugangsdaten (Passwort oder API-Token), Server-Karte, Fehler.
2. **Start**: Gruß, Profil-Initiale, Suchfeld, Bewertungs-Karte nach dem Kochen, „Heute" (ein
   Gericht groß, mehrere als Karussell, leer als Karte), Karussells: Mahlzeit der Tageszeit,
   „In 30 Minuten fertig", „Neu hinzugefügt", „Favoriten", „Lange nicht gekocht"; Laden, Fehler,
   offline. Langes Drücken auf Karten → Aktions-Sheet.
3. **Rezepte**: Titel, Sortier-Icon, Suchfeld, Filter-Chips (Mahlzeiten, Schnell, Favoriten,
   Kategorien/Schlagwörter mit Sheet), Kochbuch-Karussell, Raster, FAB „Rezept hinzufügen";
   Sortier-Sheet, Kategorien-Sheet; Laden, leer, nichts gefunden, Mahlzeit ohne Rezepte, Fehler.
4. **Sammlung** (Kochbuch, Kategorie, Schlagwort, Utensil, Mahlzeit, Schnell, Favoriten).
5. **Rezeptseite**: Hero 4:3 mit schwachem Verlauf (28 %), Foto-Kreise (Zurück, Herz, ⋯),
   Titel, Sterne (auch leer) mit Quelle, Mint-Buttons Einplanen/Einkaufen, KI-Überarbeitung mit
   Fortschritt, Beschreibung, Zeiten-Karte, Kategorie-Chips mit Stift, Zutaten (Stepper,
   Maßeinheiten-Dropdown ohne Beschriftung, Karten mit Haarlinien), Utensilien, Schritte mit
   Salbei-Ziffern, Notizen-Karten, Nährwerte-Karte, FAB „Jetzt kochen". Menü ⋯, Lösch-Dialog,
   Bewertungs-Sheet, KI-Sheet, Kategorien-Sheet, Planen-Sheet, Einkaufs-Sheet; Laden, Fehler,
   offline.
6. **Kochmodus**: Kopf mit Rezeptname und „Zutaten", Fortschritt, Schritt-Seiten mit
   Timer-Links und Timer-Buttons, Zutaten-Panel (aufziehbar), Zutaten-Sheet mit Segmenten,
   Timer-Karte, Hinweis auf genaue Wecker, Abschluss-Seite (Bewertung, „Als heute gekocht
   markieren").
7. **Wochenplan**: rollierende sieben Tage ab heute, Kopf mit Korb-Icon (Woche einkaufen),
   Zeitraum/KW mit Pfeilen, Tageskarten mit Wochentag als Überschrift, Einträge,
   Wischen zum Entfernen, Eintrags-Sheet, Verschieben-Sheet, Mahlzeit-planen-Sheet
   (Rezeptsuche/Notiz), Bestätigungsdialog; Laden, Fehler, offline.
8. **Einkauf**: Kopf mit Einkaufsmodus und Menü, Listenwechsel, Eingabefeld, Gruppen nach
   Labels, „Im Korb" mit „Leeren", Wischen zum Löschen, langes Drücken bearbeitet; Dialoge
   (neue Liste, umbenennen, löschen, Artikel bearbeiten); leer, alles erledigt, keine Liste,
   Laden, Fehler, offline, ausstehende Änderungen.
9. **Einstellungen**: Konto-Karte, Gruppen (Darstellung, Sprache, Kochen, Offline, Über Aroma),
   Dropdown-Auswahlen, Schalter, Update-Zeile, Abmelden; Abmelde-Dialog. **Lizenzen** mit Dialog.
10. **Rezept hinzufügen**: Link/Text, Einfügen-Angebot, Fotos, KI-Chips, Import, Fortschritt,
    Fehler, andere Wege (Fotos, selbst schreiben mit Namensdialog).
11. **Bearbeiten**: Foto, Felder, Art des Gerichts, Zutaten- und Schrittzeilen, Kategorien,
    Schlagwörter; Laden, Fehler.
12. **Widgets** (Glance): Heute, Einkaufsliste.

### Screenshot-Setup

Roborazzi 1.79 mit Robolectric (`GraphicsMode.NATIVE`), `ScreensScreenshotTest` (de/en ×
hell/dunkel, 411 × 891 dp, xxhdpi), `WidgetScreenshotTest`, `SampleData`, `TabFrame`. Fotos sind
erzeugte Verläufe. Ausgabe: `app/build/outputs/roborazzi`, CI lädt sie als Artefakt hoch.
Für `docs/design/screenshots/` werden die Bilder auf zwei Drittel verkleinert und als WebP
gespeichert (gut lesbar, klein genug fürs Repository).

## P1 – Fehler (erledigt)

1. **Umbruch.** Ursache per Render-Experiment gefunden: Titel waren mit `LineBreak.Heading`
   (ausgeglichener Umbruch) und `Hyphens.None` gesetzt. In dieser Kombination bricht Androids
   Balanced-Breaker Wörter an beliebiger Stelle ohne Trennstrich, um Zeilen auszugleichen
   („Zitronen-Baiser-Pl / ätzchen", „Aubergi / nen"). Zweite Ursache: Android trennt kein Wort, das
   schon einen Bindestrich enthält; „Erdbeer-Streuselkuchenschnitten" brach deshalb ohne
   Trennstrich. Behebung: alle Rollen mit `Hyphens.Auto`, Titel weiter ausgeglichen; eine eigene
   `Text`-Komponente setzt nach jedem Bindestrich zwischen Buchstaben ein Null-Breiten-Leerzeichen
   (U+200B), damit jedes Teilwort einzeln getrennt wird, und ersetzt U+2011 durch „-". Ein Umbruch
   vor dem Bindestrich ist damit ausgeschlossen. Geprüft mit `StaticLayout` (Robolectric lädt die
   echten Trennmuster) und Tests (`BreakableTest`).
2. **Statusleiste.** Der Verlauf hinter der Statusleiste ist einer deckenden Fläche in `background`
   gewichen (`StatusBarCover`); nichts läuft mehr sichtbar darunter durch.
3. **Hero.** Scrim 120 dp, 45 % → 0 %; solange das Foto unter der Statusleiste liegt, sind ihre
   Icons hell (`StatusBarOverPhoto`).
4. **FABs entfernt.** Rezeptseite: Aktionsleiste mit Einplanen, Einkaufen und „Jetzt kochen";
   Rezepte: „Rezept hinzufügen" als Icon im Kopf. `FloatingAction` gelöscht.
5. **Kochmodus.** Das ziehbare Zutaten-Panel nahm geöffnet immer 60 % der Höhe ein. Ersetzt durch
   eine Box im Schritt, nur so hoch wie ihr Inhalt, mit „Für diesen Schritt · 0 von 2".
6. **Zeiten.** `TimeText` liest ISO 8601 und Freitext (Spannen, „ca.", Stunden und Minuten), die
   Anzeige schreibt „45 Min.", „1 Std. 20 Min.", „10–15 Min." mit U+202F. Fehlt die Gesamtzeit,
   ist sie die Summe aus Vorbereitung und Garzeit. „0 Min." und Leeres entfallen. Was zu Mealie geht
   (Editor, KI-Überarbeitung), behält normale Leerzeichen.
7. **Sterne** erscheinen nur bei einer Bewertung; „Rezept bewerten" steht im Menü ⋯. Karten zeigen
   keine Bewertung mehr, sondern Zeit oder ersatzweise die Kategorie.
8. **Maßeinheiten** als beschriftete Textaktion „Einheiten: wie im Rezept", die ein Sheet öffnet.

## P2 – Grundlagen und Komponenten (erledigt)

**Theme (`ui/theme`).** `AromaTheme` stellt per CompositionLocal Farben (`AromaColors`, Tokens aus
4.1, hell und dunkel), Typografie (`AromaTypography`, genau die Rollen aus 4.2, dazu die
Profil-Initiale aus 6.7), Abstände (`Space`), Formen (`AromaShapes`) und Schatten (`AromaShadows`)
bereit. Das Material-`ColorScheme` wird nur auf die Tokens abgebildet, damit Material-Teile keine
Mint- oder Tonal-Container mehr zeigen. Kein Ripple: Drücken tönt mit `ink` 6 % (`PressTint`),
Karten und Boxen geben auf 0,98 nach. Material You ist standardmäßig aus und färbt eingeschaltet nur
`accent` und `accentSoft`.

- Tokens: Abstände 4–64, Seitenrand 24, Box 16 innen und 12 untereinander, Abschnitte 40, Titel bis
  Inhalt 12, Kopf 16/4/24, Raster 16/28, Radien 20/14/12/28, Bewegung 150/250/350 ms, Bilder 200 ms,
  Skeleton 1,2 s, Touch-Ziel 48.
- Schatten: mehrschichtige `dropShadow`-Ketten nach 4.5 in `shadow` (#3A2E22, dunkel Schwarz mit
  doppelter Deckkraft); die Kopfleiste blendet ihren Schatten in 150 ms ein, sobald Inhalt
  darunter liegt.
- Schriften: Newsreader (Display, Subhead, Text, Text kursiv) und Instrument Sans 400/500; Figtree
  entfernt.
- Icons: neu im bestehenden Stil Besteck, Flamme, Uhr, Lesezeichen, Regler, Blatt, Pfeil nach rechts
  oben und die Einstellungs-Icons; aktive Tabs mit 2-dp-Strich statt Duoton.

**Komponenten (`ui/components`), jede genau einmal.**

| Bereich | Neu | Ersetzt |
|---|---|---|
| Kopf | `LargeHeader`, `TopBar` (56 dp, `barTitle`, Schatten statt Linie), `DetailPage`, `SectionTitle`, `BackButton`, `StatusBarCover`, `StatusBarOverPhoto` | `PageHeader`, `HeaderAction`, `DetailTopBar`, `LargeTitle`, `SectionHeader` |
| Navigation | `TabBar` (64 dp, gleitende Pill 56 × 32 in `accentSoft`, Schatten) | Leiste mit Haarlinie |
| Buttons | `PrimaryButton` (Tinte, 48/36/56 dp), `SecondaryButton` (Tonfläche), `TextAction` (Salbei), `IconButton` (Kreis 40 dp, in Leisten 48 dp, auf Fotos Papier 90 %), `ButtonProgress` | `FloatingAction`, `QuietLink`, `IconAction`, `TonalIconButton`, `OnPhotoButton`, `BarIconButton`, Mint-Varianten |
| Flächen | `TonalBox`, `TonalBoxPart`, `BoxHeader`, `GroupHead`, `Separator` (die einzige Linie), `IconCircleMark` | `SoftCard`, `CardPart`, `CardGroup`, `GroupLabel`, `Hairline`, `DayHeader` |
| Inhalt | `RecipeCard` (4 : 5, Radius 14, Innenkontur, Lesebändchen, zwei Zeilen reserviert), `RecipeRow`, `MetaLine`, `RecipeImage`, `Ribbon`, `IngredientList`, `RecipeGrid` | `PhotoCard`, `RecipeListItem`, `RecipeMeta` |
| Auswahl | `Stepper` (Pill 40 dp), `SelectionRow` (gleitende Pill), `Segment`, `CheckCircle`, `SettingsRow` mit Schalter, `RatingStars` | `AromaFilterChip`, `MenuChip`, `SegmentedControl`, `PageTabs`, `SwitchRow` |
| Eingabe | `PillField` (Suchfeld 48 dp), randlose Felder auf Tonfläche | `PillTextField`, `SearchPill`, umrandete Felder |
| Ebenen | `AromaBottomSheet` (`bar`, Radius 28, Griff 32 × 4), `AromaDropdownMenu`, `AromaAlertDialog`, Snackbar (`ink`), `BottomActionBar` (72 dp) | weiße Flächen mit Haarlinien |
| Zustände | `EmptyState`, `ErrorState` (eine Zeile mit „Erneut versuchen"), `ErrorNote`, `Skeleton`, `SkeletonLine`, `OfflineNotice` | `MessageState`, `Placeholder`, `PlaceholderLines`, `InlineProgress` |
| Text | `Text` (Umbruch nach Bindestrichen, U+2011 → „-") | `material3.Text` |

**Migration.** Alle Screens nutzen nur noch die neuen Komponenten; die alten sind gelöscht.
Einkauf, Einstellungen, Kochmodus, Rezepte und Sammlungen sind dabei schon nach 6.4–6.7 aufgebaut
worden, weil die Migration sie ohnehin ganz berührte. Start, Wochenplan und Rezeptseite sind nur
umgestellt und werden in P3/P4 neu aufgebaut.

**Prüfungen.** `DesignRulesTest` prüft jetzt: Größen nur aus Tokens oder benannten Konstanten,
Abstände nur auf der Skala, Farben nur aus dem Theme, keine Flächen in #FFFFFF/#EFEEEB/#D6E7DA
(Palette und Ressourcen), Formen und Schrift nur aus dem Theme, `Text` statt `material3.Text`,
keine Großbuchstaben und keine Laufweite, keine Rahmen, keine Divider, keine FABs, keine
Material-Bausteine in Screens, Bewegung aus `Motion`, Kontrast ≥ 4,5 : 1 für jede Paarung von Schrift
und Grund (hell und dunkel). `TypeRolesTest` hält die Rollen deckungsgleich mit der Tabelle in 4.2
(Schrift, Größe, Zeilenhöhe, Gewicht, Kursiv, Tabellenziffern, Silbentrennung, Sprache).
`WidgetPaletteTest` hält die Widgets in der neuen Palette.

## Entscheidungen

1. **Branch** `design/kochbuch`, wie im Auftrag.
2. **Serif bleibt Newsreader**: Sie hat einen echten Kursivschnitt; er fehlte nur in der App und
   kommt dazu. Statische Instanzen für die optischen Größen (Display, Subhead, Text, Text kursiv),
   nur Gewicht 400, beschnitten auf Latein. Die Medium-Instanz entfällt.
3. **Sans Instrument Sans** 400 und 500, statisch instanziiert (Breite 100). Der Schrift fehlen
   die Bruchzeichen (½ ⅓ ¼ ¾ ⅔ ⅛ ⅜ ⅝ ⅞) und U+202F; beide werden als zusammengesetzte Glyphen
   aus den eigenen Ziffern ergänzt (OFL erlaubt Änderungen, keine reservierten Namen).
4. **Rasterabstand 28 dp** zwischen Rezeptkarten-Reihen, obwohl 28 nicht in der Abstandsliste
   steht: Abschnitt 4.3 fordert ihn ausdrücklich; die spezielle Vorgabe geht der allgemeinen vor.
5. **Icon-Buttons in der Kopfleiste 40 dp.** 4.6 nennt für Leisten 48 dp; in der 56-dp-Kopfleiste
   blieben einem 48-dp-Kreis nur 4 dp Luft, und die Kreise würden beim Einklappen springen. Die
   unteren Leisten (Navigation, Aktionsleisten) haben 48-dp-Kreise, Kopf und Kopfleiste 40 dp.
6. **Titel von Sheets und Dialogen in der Sans** (`labelLarge`), weil 4.2 dafür keine Serif-Rolle
   vorsieht; steht ein Rezeptname im Titel, ist er `cardTitle`.
7. **Lade-Kreisel nur in Buttons** (5.21). Zeilen, die etwas laden (Update-Suche, Rezept anlegen),
   sagen es in ihrer zweiten Zeile; der Import zeigt seinen Fortschritt an der Stelle seines Buttons.
8. **Fehler am Ort** (`ErrorNote`): unter Feld oder Button ein Titel in `error` und der Grund in
   `bodySmall`; Seitenfehler sind eine Zeile mit „Erneut versuchen" (6.8).
9. **Einkauf:** Einkaufsmodus, Listenwechsel und Listenverwaltung stehen im Menü ⋯; „Zutaten aus dem
   Wochenplan hinzufügen" übernimmt die Rezepte der nächsten sieben Tage, wie „Woche einkaufen"
   im Wochenplan.
10. **Zutatenzeilen** sind 40 dp hoch, antippbare (Kochmodus) 48 dp, wegen der Touch-Ziele.
11. **Widgets** folgen der neuen Palette (Papier, Tinte, Salbei; Button in Tinte), mit Material You
    nur im Akzent wie die App.

## Abweichungen

1. Icon-Buttons in der Kopfleiste 40 statt 48 dp (Entscheidung 5).

## Offene Punkte

(noch keine)
