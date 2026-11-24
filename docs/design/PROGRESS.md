# Design-Runde „Kochbuch" – Arbeitsstand

Gedächtnis dieser Runde. Auftrag: [`AUFTRAG.md`](AUFTRAG.md). Branch: `design/kochbuch`.
Wird nach jedem Schritt aktualisiert.

## Phasen

- [x] P0 Bestandsaufnahme, Vorher-Screenshots (`screenshots/vorher/`, 308 Bilder)
- [ ] P1 Fehler beheben (Abschnitt 8)
- [ ] P2 Grundlagen (4) und Komponenten (5), alte Varianten migriert und gelöscht
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

## Abweichungen

(noch keine)

## Offene Punkte

(noch keine)
