# Design-Runde „Kochbuch" – Arbeitsstand

Gedächtnis dieser Runde. Auftrag: [`AUFTRAG.md`](AUFTRAG.md). Branch: `design/kochbuch`.
Wird nach jedem Schritt aktualisiert.

## Phasen

- [x] P0 Bestandsaufnahme, Vorher-Screenshots (`screenshots/vorher/`, 308 Bilder)
- [x] P1 Fehler beheben (Abschnitt 8)
- [x] P2 Grundlagen (4) und Komponenten (5), alte Varianten migriert und gelöscht
- [x] P3 Rezeptseite
- [x] P4 Start und Wochenplan
- [x] P5 Rezepte, Kochmodus, Einkauf, Einstellungen, übrige Screens, Sheets, Dialoge, Zustände
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

## P3 – Rezeptseite (erledigt)

Aufbau nach 6.1, von oben nach unten:

- **Hero** 4 : 3 bis an den Rand (im Querformat höchstens 60 % der Fensterhöhe), Scrim 120 dp
  von 45 % auf 0 %, Statusleiste hell, solange das Foto darunter liegt. Zurück, Merken und ⋯ als
  Icon-Buttons auf Papier mit 90 %. Gemerkt: Der Merken-Button weicht, das Lesebändchen (16 × 52 dp)
  gleitet in 250 ms von der Oberkante herab, wo er stand; ein Tippen darauf hebt das Merken auf,
  die Snackbar bietet „Rückgängig". Merken gibt haptisches Feedback.
- **Titelblock** 24 dp unter dem Foto: `recipeTitle`; Unterzeile `subtitle` mit Kategorie, bis zu
  zwei Tags und der Quelle als Domain ohne „www" mit ↗ in `accent`; die Quelle ist ein eigenes
  Element und bricht nur als Ganzes um. Sterne nur bei einer Bewertung, eigene Zeile, 14 dp,
  `accent`, die Zahl in `meta`.
- **Kopfnote** in `headnote` 16 dp darunter.
- **Kennzahlen-Box** 24 dp darunter: Vorbereitung (Besteck), Kochen oder Backen (Flamme), Gesamt
  (Uhr); fehlende Werte entfallen, ohne Werte entfällt die Box. „Backen", wenn Name, Kategorie oder
  Tag ein Backrezept nennen (Kuchen, Plätzchen, Gebäck, Torte, Brot, Muffin …; „No-Bake" nicht).
- **Zutaten** 40 dp darunter: Titel `section`, rechts der Stepper mit der Einheit aus dem Ertrag
  („1 Blech", „4 Portionen"). Darunter die Metazeile mit eigenem Ertrag („ca. 24 Stück", skaliert)
  und der Textaktion „Einheiten: wie im Rezept", die ein Sheet öffnet. Gruppen in `groupLabel`
  24 dp davor, 8 dp danach. Zeilen 40 dp ohne Linien und Boxen, Mengenspalte rechtsbündig, so breit
  wie die breiteste Menge, mindestens 56 dp, in `accent` und `tnum`; Zutaten ohne Menge in der
  Namensspalte. Utensilien als letzte Gruppe.
- **Zubereitung** 40 dp darunter: Ziffern in `numeral` und `inkSecondary` in einer 40-dp-Spalte,
  Text in `body`, 24 dp zwischen den Schritten, die Zutaten des Schritts als `meta` darunter.
- **Notizen und Nährwerte** offen mit Titel in `section`.
- **Aktionsleiste:** Einplanen und Einkaufen als Icon-Buttons (48 dp), „Jetzt kochen" mit Topf als
  einziger Primär-Button; der letzte Inhalt endet 24 dp über der Leiste.
- **Kopfleiste:** eingeklappt mit dem Rezeptnamen, sobald das Foto darunter verschwunden ist;
  Schatten nur dann.
- **Verwaltung** nur im Menü ⋯: Bearbeiten, Kategorien und Schlagwörter (mit der Art des
  Gerichts), Bewerten, Mit KI überarbeiten, Teilen, Löschen. Das Bild ändert man im Bearbeiten.
- **Ohne Foto** gibt es keinen Platzhalter (Abschnitt 7): Die Seite beginnt wie jede Detailseite
  mit Zurück, Merken und ⋯ und dem Titel.

Übergreifend dabei behoben:

- Der Trennpunkt „ · " hat ein geschütztes Leerzeichen davor: Keine Zeile beginnt mit „·".
- Schritttexte binden Zahl und Einheit mit U+202F („7 Minuten", „180 °C"), auch im Kochmodus.
- Gekürzte Texte enden nach einem ganzen Wort mit „…" statt mitten im Wort.
- Daten: `Recipe.yieldText` kommt aus dem vorhandenen DTO-Feld `recipeYield`; nur Anzeige,
  API, DTO und Offline-Kopie bleiben unverändert.

Geprüft mit `DesignScreenshotTest` (Testtexte aus Abschnitt 14: voll mit 30 Zutaten in vier
Gruppen, nur ein Name, längster Titel mit „1 Blech"; hell und dunkel, 100, 130 und 200 %, auch als
ganze Seite).

## P4 – Start und Wochenplan (erledigt)

**Start (6.2).**

- Kopf „Guten Morgen/Tag/Abend, {Vorname}" (5–11, 11–18, ab 18 Uhr) mit dem Datum als Unterzeile
  („Dienstag, 24. November"); Suche und Profil-Initiale als Icon-Buttons, kein Suchfeld.
- Box „Heute": die nächste Mahlzeit des Tages mit Bild 16 : 10 oben (die Box rundet seine oberen
  Ecken), „Heute Abend" und Zeit, Rezeptname in `section` (höchstens zwei Zeilen), „Jetzt kochen"
  (Primär, kompakt, öffnet den Kochmodus) und „Rezept" (Sekundär, kompakt); die übrigen Mahlzeiten
  des Tages als Rezeptzeilen. Ohne Plan heißt sie „Für heute ist noch nichts geplant" und zeigt drei
  Vorschläge mit „Einplanen", das sie für die Mahlzeit der Stunde auf heute legt.
- Box „Diese Woche": „5 von 7 geplant" mit Chevron, sieben Spalten Mo–So, heute im Tintenkreis,
  Punkte unter geplanten Tagen, Vergangenes in `inkTertiary`; ein Tag öffnet den Wochenplan dort.
- Box „Einkaufsliste" nur bei offenen Artikeln (der zuletzt genutzten Liste): Korb im Icon-Kreis,
  „12 Artikel offen", Chevron.
- Bewertung nach dem Kochen als Box unter den drei Boxen (bestehende Funktion).
- Inspiration, offen: „Ideen für heute" mit „Alle" als Karussell (Karten 148 dp, die dritte
  angeschnitten, den ganzen Tag dieselbe Auswahl), „Schnell gemacht" als drei Zeilen (höchstens 30
  Min.), „Neu in deiner Sammlung" groß (4 : 3, Radius 14, Name, zwei Zeilen Kopfnote, Metazeile).
  Kein Rezept zweimal auf der Seite; ein Abschnitt mit weniger als drei Rezepten entfällt.
- Akzeptanz: Kopf, ganze Heute-Box und Anfang von „Diese Woche" stehen ohne Scrollen auf dem
  Bildschirm.

**Wochenplan (6.3).**

- Kalenderwoche Montag bis Sonntag statt sieben Tagen ab heute; Unterzeile „23.–29. November ·
  KW 48", über den Monatswechsel „30. Nov.–6. Dez. · KW 49", außerhalb der aktuellen Woche mit
  „Zur aktuellen Woche". Wochenwechsler als Pill 40 dp mit ‹ und ›; Wischen wechselt die Woche,
  die neue schiebt sich von ihrer Seite herein.
- Agenda ohne Linien, 8 dp zwischen den Tagen (in P5 auf 24 dp erweitert); links 44 dp mit Kürzel
  (`meta`) über der Ziffer (`numeral`, `inkSecondary`); rechts Rezeptzeilen mit „Abendessen ·
  45 Min.", 8 dp untereinander (in P5 12 dp),
  und ein Plus für eine weitere Mahlzeit. Leere Tage: „Gericht planen", höchstens 56 dp.
- Heute auf `surface` (Radius 20), 12 dp über die Spalte hinaus, „Heute" in `accent` (500), Ziffer
  in Tinte. Vergangene Tage in `inkTertiary`, Bilder mit 60 %; ab zwei vergangenen Tagen eine
  aufklappbare Zeile „Mo–Mi · 4 Gerichte".
- Box „Zutaten der Woche" am Ende: Korb im Icon-Kreis, „aus 7 Gerichten · 46 Artikel", „Auf die
  Liste" (Sekundär, kompakt); sie ersetzt das Korb-Icon im Kopf. Die Artikel zählen die Zutaten der
  Rezepte aus der Kopie auf dem Gerät oder von Mealie.
- Tippen öffnet das Rezept, langes Drücken das Sheet mit Einkaufen, Verschieben und Entfernen;
  TalkBack bekommt Einkaufen und Entfernen direkt an der Zeile.

**Verschoben.** Vom Start: „Favoriten" → Rezepte, Filter „Gemerkt"; „Neu hinzugefügt" → „Neu in
deiner Sammlung" und Rezepte (Sortierung „Neueste"); „In 30 Minuten fertig" → „Schnell gemacht"
und Rezepte, Filter „Schnell"; „Lange nicht gekocht" → Rezepte, Filter „Lange nicht gekocht"
(neu im Filter-Sheet, gleiche Abfrage wie vorher). Im Wochenplan: Wischen zum Entfernen → „Entfernen"
im Sheet (Wischen wechselt jetzt die Woche); Korb im Kopf → Box „Zutaten der Woche".

## P5 – Übrige Screens, Sheets, Dialoge, Zustände (erledigt)

**Rezepte (6.4).** Kopf, Suchfeld und Kategorien wie in P2 angelegt; eine Suche zeigt ihre
Ergebnisse als Rezeptzeilen statt als Raster (6.8). Findet die Suche nichts oder ist eine Kategorie
leer, steht „Filter zurücksetzen" im Leerzustand. Das Filter-Sheet hat „Lange nicht gekocht" (P4).

**Kochmodus (6.5).** Wie in P1 und P2 umgebaut; die laufenden Timer enden 24 dp über der Leiste,
die Leiste hat ringsum 16 dp (mit den 56-dp-Buttons 88 dp hoch).

**Einkauf (6.6).** Gruppen als Boxen mit Kopf in `label`, „Erledigt (n)" eingeklappt, Menü ⋯ für
Einkaufsmodus und Listen (Entscheidung 9). Der Leerzustand setzt die lange Textaktion „Zutaten aus
dem Wochenplan hinzufügen" unter seinen Text, statt ihn zu quetschen.

**Einstellungen (6.7).** Profil-Box mit Initiale, Name, E-Mail und Serverzeile; Gruppen mit Kopf in
`label`. Wert oder Schalter stehen jetzt auf der Titelzeile, die Beschreibung läuft darunter über die
ganze Breite (Entscheidung 24).

**Übrige Screens (6.8).** Rezept hinzufügen (alle Zustände: leer, Link, Text, Fotos, Fotos nicht
lesbar, Import läuft, Fehler), Bearbeiten (Felder, Zeilen, Art des Gerichts, Kategorien und
Schlagwörter), Anmeldung (Server, Zugangsdaten), Sammlungen, Lizenzen, die Sheets (Menü ⋯,
Kategorien und Schlagwörter, Bewerten, Mit KI überarbeiten, Filter, Einkaufen, Einplanen) und die
Lade- und Fehlerzustände folgen denselben Komponenten. Dabei korrigiert:

- Icon-Buttons in Feldern (Passwort zeigen) stehen auf `surfaceInBox`, sonst verschwand ihr Kreis
  auf der Feldfläche.
- Der Schriftzug „Aroma" bei der Anmeldung steht in Tinte wie jeder Seitentitel (Entscheidung 28).
- Zahlenfelder im Bearbeiten stehen auf einer Linie, auch wenn ein Label bei großer Schrift zwei
  Zeilen braucht.
- Zwischen Auswahlsegmenten liegen 4 dp; zwei gewählte Schlagwörter stießen sonst aneinander.

**Leisten.** Auf Rückmeldung: Über den Icons der Navigationsleiste war zu wenig Luft (6 dp über der
Pill). Die Leiste ist jetzt 80 dp hoch, mit 16 dp über der Pill und 12 dp unter der Beschriftung
(Entscheidung 21). Alle Aktionsleisten haben ringsum 16 dp (Entscheidung 22).

**Luft im Kopf und im Wochenplan.** Ebenfalls auf Rückmeldung: Unter dem Zurück-Kreis blieben bis
zum Titel knapp 12 dp; jetzt folgt der Titel 16 dp unter der Zeile des Zurück-Buttons (sichtbar rund
28 dp), auf jeder Detailseite und auf der Rezeptseite ohne Foto (Entscheidung 29). Im Wochenplan
liefen die Tage mit 8 dp ineinander; Tage stehen jetzt 24 dp auseinander, die Gerichte eines Tages
12 dp wie Rezeptzeilen überall (Entscheidung 30). Danach alle Screens in doppelter Größe gezielt auf
Abstände durchgesehen: Kopf, Abschnitte, Boxen, Listen, Sheets und Leisten.

**Große Schrift.** Bei 200 % wurden Beschriftungen der Navigation abgeschnitten („Wochenp…"); sie
wachsen jetzt bis 130 % mit (Entscheidung 23). Bei großer Schrift steht „Einplanen" in der leeren
Heute-Box unter der Metazeile, statt die Namen auf eine schmale Spalte zu drücken (Entscheidung 26).

Geprüft mit `DesignScreenshotTest` (Rezepte voll, Suche und leer; Einkauf voll, ganz und leer;
Kochmodus mit und ohne Zutaten; Einstellungen, auch ganz; je hell und dunkel, 100, 130 und 200 %)
und `ScreensScreenshotTest`, der alle Screens, Sheets und Zustände jetzt auch mit 130 % Schrift in
hell und dunkel rendert.

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
10. **Zutatenzeilen** sind 40 dp hoch, im Kochmodus mit Abhak-Kreis 48 dp (siehe 15).
11. **Widgets** folgen der neuen Palette (Papier, Tinte, Salbei; Button in Tinte), mit Material You
    nur im Akzent wie die App.
12. **Lesebändchen im Hero:** sichtbar 52 dp lang unter der Statusleiste, dahinter reicht es bis an
    die Fotokante; sonst bliebe es auf Geräten mit hoher Statusleiste ein Stummel. Sein Fuß steht
    auf Höhe der Knöpfe.
13. **Nur die Quelle ist in der Unterzeile antippbar** (6.1); Kategorie und Tags sind Angaben.
    Sammlungen nach Kategorie oder Schlagwort öffnen sich über Rezepte → Filter.
14. **Stepper ohne Ertragsangabe** zeigt den Faktor („Menge ×1") statt nur „Portionen": Ohne Zahl
    sähe man nicht, wie stark skaliert ist.
15. **Zutatenzeilen 40 dp**, auch wo sie zum Abhaken antippbar sind (Rezeptseite): Mit 48 dp wirkte
    die Liste wie eine Einstellungsliste; die Ziele grenzen lückenlos aneinander und sind so breit wie
    die Seite. Im Kochmodus, mit Abhak-Kreis, 48 dp.
16. **Rezept ohne Foto:** kein Hero und kein Platzhalter, sondern der Kopf einer Detailseite (7).
17. **Gruß in zwei Zeilen:** „Guten Morgen," steht über dem Namen. Ausgeglichen gesetzt hätte der
    Satz sonst „Guten Mor-/gen, Mara" getrennt.
18. **„Einplanen" in der leeren Heute-Box** legt das Rezept auf heute, als Mahlzeit der Stunde, ohne
    Sheet; der Wochenplan hält alles Weitere bereit.
19. **Vergangene Tage falten sich nur in der laufenden Woche**; eine Woche in der Vergangenheit, die
    man bewusst aufruft, steht offen.
20. **„Heute" in `meta`-Größe** (500, Salbei): als Kürzelersatz in der 44-dp-Spalte; in `label`
    stieß es ans Bild.
21. **Navigationsleiste 80 dp** statt 64 dp: 16 dp über der Pill, 12 dp unter der Beschriftung, der
    Systemrand kommt darunter. Mit 64 dp blieben über der Pill 6 dp; die Icons stießen fast an die
    Kante (Rückmeldung während der Runde).
22. **Aktionsleisten mit 16 dp ringsum**, wie 5.17 den Innenabstand nennt: 80 dp mit 48-dp-Buttons
    statt 72 dp, im Kochmodus 88 dp mit 56-dp-Buttons. Mit 72 dp blieben über den Buttons 12 dp,
    im Kochmodus 8 dp.
23. **Beschriftungen der Navigation wachsen bis 130 %** mit der Systemschrift. Vier Ziele teilen sich
    die Breite; mit 200 % wurde „Wochenplan" abgeschnitten.
24. **Einstellungszeilen:** Wert mit Chevron oder Schalter auf der Titelzeile, die Beschreibung
    darunter über die volle Breite, das Icon auf Höhe des Titels. Neben „Wie im Rezept" wurden lange
    Beschreibungen sonst zu schmalen Spalten voller Trennungen.
25. **Leerzustand:** Passen Text und Textaktion nicht in eine Zeile, steht die Aktion darunter.
26. **Aktion einer Rezeptzeile** steht ab Androids Schriftgröße „Groß" (115 %) unter der Metazeile.
27. **4 dp zwischen Auswahlsegmenten**, in Auswahlreihen und bei Mehrfachauswahl.
28. **„Aroma" bei der Anmeldung in Tinte**: ein Seitentitel wie die anderen; Salbei bleibt Auswahl
    und Aktionen vorbehalten.
29. **Vom Zurück-Button zum Titel 16 dp:** Der Button steht in der 56-dp-Zeile der eingeklappten
    Kopfleiste (er springt beim Einklappen nicht), der Titel folgt 16 dp darunter. Ohne diesen
    Abstand saß der Kreis fast auf dem Titel.
30. **Wochenplan: 24 dp zwischen den Tagen, 12 dp zwischen den Gerichten eines Tages** statt je
    8 dp (6.3). Mit 8 dp waren Tage und Gerichte nicht zu unterscheiden; 12 dp gelten auch sonst
    für Rezeptzeilen (5.9).

## Abweichungen

1. Icon-Buttons in der Kopfleiste 40 statt 48 dp (Entscheidung 5).
2. Zutatenzeilen auf der Rezeptseite sind 40 dp hohe Touch-Ziele statt 48 dp (Entscheidung 15).
3. Stepper ohne Ertragsangabe zeigt den Faktor statt „Portionen" (Entscheidung 14).
4. Navigationsleiste 80 statt 64 dp (Entscheidung 21).
5. Aktionsleiste 80 statt 72 dp, im Kochmodus 88 dp (Entscheidung 22).
6. Beschriftungen der Navigation wachsen nur bis 130 % (Entscheidung 23).
7. Wochenplan mit 24 dp zwischen den Tagen und 12 dp zwischen Gerichten statt 8 dp (Entscheidung 30).

## Offene Punkte

(noch keine)
