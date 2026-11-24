# Aroma – Design-Runde „Kochbuch"

Das ist ein großer, verbindlicher Arbeitsauftrag: eine vollständige Überarbeitung von UI und Design. Du arbeitest direkt in diesem Repo und siehst den Code. Den aktuellen Stand renderst du selbst.

**Zuerst:** Speichere diesen Auftrag unverändert als `docs/design/AUFTRAG.md` und committe ihn. Schau dort nach, wann immer du unsicher bist oder der Kontext zurückgesetzt wurde. Antworte und dokumentiere auf Deutsch.

**Ergebnis:** ein Entwurf (Draft) des nächsten Releases, den ich vorab testen kann. Deine letzte Nachricht enthält den Link dazu. Veröffentliche nichts (siehe Abschnitt 12).

## 0. Auftrag

Du bist Lead Product Designer und Android-UI-Engineer in einer Person. Bring Aroma (Android-Client für Mealie) auf das Niveau eines sehr hochwertigen Kochbuchs: ruhig, luftig, typografisch präzise, stilvoll und gewählt. Das gilt für alle Screens, im hellen wie im dunklen Modus.

Dieser Auftrag ist die Zielvorgabe. Was er nicht regelt, entscheidest du nach den Leitprinzipien (Abschnitt 2) und dokumentierst die Entscheidung. Setze alles in einem Durchgang um, ohne Zwischenfreigaben. Halte nur bei echten Blockern an, etwa einer fehlenden API oder einem Risiko für Nutzerdaten.

## 1. Ausgangslage

Die Basis stimmt: warmes Papierweiß, Serif und Sans, Salbei als Akzent, große Fotos, ein durchdachter Kochmodus und Texte mit Persönlichkeit. Trotzdem wirkt die App generisch und unruhig:

- Die Farben sind weitgehend unveränderte Material-3-Rollen. Die Navigationsleiste ist reines Weiß (#FFFFFF) auf Papier (#FBF9F7). Es gibt zwei verschiedene Mints (#D6E7DA, #DCE5DC). Karten, Chips, Suchfeld, Stepper und Dropdown haben alle dasselbe Grau (#EFEEEB), sodass sich Bedienelemente und Inhaltsflächen nicht unterscheiden lassen.
- Es gibt zu viele Komponenten-Stile, allein auf der Rezeptseite sieben.
- Die Serif steht überall, deshalb fällt nichts mehr auf.
- Die Hierarchie ist verdreht: Im Wochenplan ist der Wochentag größer als das Gericht.
- Die Abstände gruppieren falsch: In den Tageskarten liegen innen 40 dp, zwischen den Karten nur 12 dp.
- Die Startseite besteht dreimal aus Überschrift plus Karussell.
- Fehler: Wörter werden mitten im Wort ohne Trennstrich umbrochen, Inhalt läuft unter die Statusleiste, Floating-Action-Buttons (FABs) verdecken Inhalte.

Zielbild ist ein Kochbuch im Zweifarbdruck aus Papier, Tinte und Salbei. Alle übrige Farbe kommt aus den Fotos.

## 2. Leitprinzipien

Bei Konflikten gilt diese Reihenfolge.

1. **Ruhe durch Ebenen, nicht durch Weglassen.** Keine Funktion fällt weg. Sekundäres wandert eine Ebene tiefer (Menü ⋯, Bearbeiten-Modus, Sheet).
2. **Zweifarbdruck.** Die UI besteht nur aus Papier-, Tinten- und Salbeitönen. Fehlerrot gibt es zusätzlich, aber nur für Fehler.
3. **Flächen statt Linien.** Gegliedert wird mit Weißraum und weichen Tonflächen. Bedienelemente haben eine Fläche, keinen Rahmen. Linien sind die Ausnahme (siehe 4.5).
4. **Boxen haben Bedeutung.** Eine Box steht für einen Zustand, eine Aktion oder Kennzahlen (Heute, Diese Woche, Einkaufsliste). Inhalt und Inspiration, also Rezepte, Zutaten und Schritte, stehen offen auf dem Papier.
5. **Schatten nur für Schwebendes** (Leisten, Sheets, Menüs). Dort sind sie weich, mehrlagig, warm getönt und gut sichtbar. Boxen im Inhalt bekommen nie einen Schatten.
6. **Nähe.** Abstände innerhalb einer Gruppe sind höchstens halb so groß wie Abstände zwischen Gruppen.
7. **Typografische Rollen.** Die Serif gibt es nur für Seitentitel, Rezeptnamen, Inhaltsabschnitte, große Ziffern und kursive Akzente. Alles, was der Bedienung dient, ist in der Sans gesetzt.
8. **Stilvoll statt Template.** Keine Labels in Großbuchstaben und keine gesperrten Mini-Überschriften über Titeln („Eyebrows"). Kontext steht als ruhige Unterzeile unter dem Titel. Ebenso tabu sind Verläufe (außer Foto-Scrims), Glas- und Blur-Effekte, Texturen, Emojis, Illustrationen, Deko-Badges, Icons vor Überschriften und Akzentfarbe als bloße Dekoration. Überall gilt normale Groß- und Kleinschreibung wie im Satz.
9. **Eine Hauptaktion pro Screen**, als Primär-Button in Tinte.
10. **Leere ist leise.** Ein Leerzustand ist eine einzige Zeile mit einer klaren Aktion und nie größer als ein gefüllter Zustand.
11. **Ein System.** Screens nutzen ausschließlich Tokens und Komponenten aus den Abschnitten 4 und 5. Im Code der Screens stehen keine eigenen Farb-, Schriftgrößen- oder Abstandswerte.

## 3. Arbeitsweise

1. **Bestandsaufnahme, noch ohne Codeänderung:** Erfasse Stack, Theme, Schriften, Icon-Familie, alle Komponenten und alle Screens mit ihren Sheets, Dialogen und Zuständen. Rendere alle Screens im aktuellen Zustand als Vergleichsbasis nach `docs/design/screenshots/vorher/`. Lege `docs/design/PROGRESS.md` an mit Inventar, Phasen-Checkliste, Entscheidungen, Abweichungen und offenen Punkten. Halte die Datei nach jedem Schritt aktuell, sie ist dein Gedächtnis.
2. **Phasen:** Arbeite auf dem Branch `design/kochbuch` und in dieser Reihenfolge. Nach jeder Phase sind Build, Lint und Tests grün, und jede Phase endet mit mindestens einem Commit.
   - P1: Fehler beheben (Abschnitt 8)
   - P2: Grundlagen (Abschnitt 4) und Komponenten (Abschnitt 5); alte Varianten migrieren und löschen
   - P3: Rezeptseite als Referenz für alles Weitere
   - P4: Start und Wochenplan
   - P5: Rezepte, Kochmodus, Einkauf, Einstellungen sowie alle übrigen Screens, Sheets, Dialoge, Lade- und Fehlerzustände
   - P6: Dark Mode, Bewegung, Haptik, Barrierefreiheit
   - P7: Art-Director-Review und Bericht (Abschnitt 11)
   - P8: Release-Draft (Abschnitt 12)
3. **Sichtprüfung ist Pflicht.** Lege für jeden Screen Previews an, jeweils hell und dunkel und in den Schriftgrößen 100 % und 130 %. Die Hauptscreens bekommen zusätzlich 200 %. Jede dieser Varianten zeigst du mit vollen Daten, ohne Daten und mit den extremen Texten aus Abschnitt 14.
   - Rendere die Previews als PNG. Nutze dafür ein vorhandenes Screenshot-Setup, sonst Roborazzi mit Compose-Preview-Unterstützung oder Compose Preview Screenshot Testing, nur im Test-Scope.
   - Sieh dir jedes Bild selbst an. Ist ein Gerät per adb erreichbar, ergänze echte Screenshots.
   - Prüfe jedes Bild gegen die Akzeptanzkriterien und korrigiere, bevor du weitermachst. Die Bilder kommen nach `docs/design/screenshots/nachher/`.
4. **Ermessen:** Sieht eine Vorgabe in der Praxis schlechter aus als eine naheliegende Alternative, die alle Leitprinzipien erfüllt, nimm die Alternative. Dokumentiere dann beide Varianten mit Begründung.
5. **Durchhalten:** Der Auftrag ist groß. Arbeite ihn vollständig ab, auch über Kontextgrenzen hinweg: Sichere den Stand in `PROGRESS.md` und per Commit, dann mach weiter. Am Ende gibt es keine TODOs, keinen auskommentierten Altcode und keine ungenutzten Komponenten.
6. **Texte:** Neue Texte kommen in die String-Ressourcen aller Sprachen, die die App unterstützt.

## 4. Grundlagen

### 4.1 Farben

| Token | Hell | Dunkel | Verwendung |
|---|---|---|---|
| `background` | #FBF9F7 | #171512 | Seitengrund, auch hinter der Statusleiste |
| `surface` | #F0EDE6 | #221F1B | Boxen und Tonflächen auf dem Seitengrund |
| `surfaceInBox` | #FBF9F7 | #2C2925 | Tonflächen innerhalb von Boxen |
| `surfacePressed` | #E6E1D8 | #36322D | Gedrückt-Zustand, Skeletons, offene Fortschrittssegmente, ausgeschalteter Schalter |
| `bar` | #FBF9F7 | #221F1B | Navigationsleiste, Aktionsleisten, Sheets |
| `ink` | #1F1D1A | #EDE9E2 | Text, Primär-Button, Icons in Icon-Buttons |
| `inkSecondary` | #6B675F | #A8A298 | Unterzeilen, Metadaten, Icons in Zeilen |
| `inkTertiary` | #8F8A81 | #77716A | nur Vergangenes und Deaktiviertes |
| `accent` | #536258 | #BACBBE | Mengen, Textaktionen, kursive Akzente, Fortschritt, Lesebändchen |
| `accentSoft` | #D6E1D7 | #3A4A3F | Auswahlzustand (Navigation, Kategorien) |
| `onInk` | #FBF9F7 | #171512 | Text und Icons auf `ink` und `accent` |
| `separator` | `ink` 8 % | `ink` 10 % | ausschließlich eingerückte Listentrenner (siehe 4.5) |
| `shadow` | #3A2E22 | #000000 | Schattenfarbe |
| `scrim` | #000000 | #000000 | Verlauf oben über Fotos, 45 % → 0 % |

- Entfernen: Flächen in #FFFFFF, #EFEEEB und #D6E7DA sowie alle Mint- und Tonal-Container aus Material.
- „Farben vom Hintergrundbild" (Material You) ist standardmäßig aus. Ist es an, kommen nur `accent` und `accentSoft` aus dem dynamischen Farbschema; Papier und Tinte bleiben.
- Text hat mindestens 4,5 : 1 Kontrast; die Paarungen oben erfüllen das. `inkSecondary` steht nie auf `accentSoft`. `inkTertiary` gibt es nur für Inhalte, die nicht wesentlich sind.

### 4.2 Typografie

- **Serif:** Die bestehende bleibt, sofern sie einen echten Kursivschnitt hat; sonst Newsreader (variabel, mit optischen Größen).
- **Sans:** Instrument Sans.
- Beide Schriften als Dateien in die App packen, keine Downloadable Fonts. Nur die Gewichte 400 und 500.
- Mengen, Zeiten und Zähler mit Tabellenziffern (`tnum`).

| Stil | Schrift | Größe / Zeilenhöhe (sp) | Einsatz |
|---|---|---|---|
| `display` | Serif 400, Laufweite −0,5 % | 34 / 40 | Seitentitel |
| `recipeTitle` | Serif 400, Laufweite −0,5 % | 30 / 36 | Titel der Rezeptseite |
| `section` | Serif 400 | 22 / 28 | Inhaltsabschnitte („Zutaten", „Zubereitung", „Ideen für heute"), Rezeptname in der Heute-Box |
| `cardTitle` | Serif 400 | 17 / 22 | Rezeptnamen in Karten, Zeilen und Agenda |
| `headnote` | Serif kursiv | 17 / 26 | Einleitungstext eines Rezepts |
| `groupLabel` | Serif kursiv, `accent` | 17 / 24 | Zutatengruppen („Mürbeteig") |
| `numeralLarge` | Serif 400 | 64 / 64 | Schrittziffer im Kochmodus |
| `numeral` | Serif 400 | 28 / 32 | Tagesziffer im Wochenplan, Schrittziffern auf der Rezeptseite |
| `barTitle` | Serif 400 | 20 / 24 | eingeklappte Kopfleiste |
| `subtitle` | Sans 400, `inkSecondary` | 15 / 20 | Unterzeile unter Seiten- und Rezepttiteln |
| `body` | Sans 400 | 16 / 24 | Fließtext, Zutaten, Schritte |
| `bodySmall` | Sans 400, `inkSecondary` | 14 / 20 | Beschreibungen |
| `labelLarge` | Sans 500 | 17 / 22 | Name im Profil |
| `label` | Sans 500 | 15 / 20 | Buttons, Box-Titel, Gruppenköpfe |
| `meta` | Sans 400, `inkSecondary`, `tnum` | 13 / 18 | Zeiten, Zähler, Quellen |
| `nav` | Sans 500 aktiv, 400 inaktiv | 12 / 16 | Navigation |
| `cookStep` | Sans 400 | 24 / 34 | Schritttext im Kochmodus |

Regeln:

- Keine Stile in Großbuchstaben und keine erweiterte Laufweite. Überall normale Groß- und Kleinschreibung; ausgenommen sind übliche Abkürzungen wie KW, TL oder EL.
- Die kleinste Schriftgröße ist 12 sp.
- **Umbruch (Deutsch):** Locale `de-DE`, automatische Silbentrennung für alle mehrzeiligen Texte, ausgewogene Zeilen bei Titeln. Umbrochen werden darf nach einem Bindestrich, nie davor. Ein Wort wird nie ohne Trennstrich umbrochen.
- Zwischen Zahl und Einheit steht ein schmales geschütztes Leerzeichen (U+202F): „500 g", „30 Min.", „1 Std. 20 Min.".
- Spannen werden mit Halbgeviertstrich ohne Leerzeichen geschrieben: „10–15 Min.", „23.–29. November".
- Mengen mit Dezimalkomma. Brüche als echte Zeichen (½ ⅓ ¼ ¾ ⅔ ⅛) statt „0,5".
- Gekürzt wird nur an Wortgrenzen, mit „…". Rezeptnamen in Karten haben höchstens 3 Zeilen; die Höhe von 2 Zeilen ist immer reserviert.

### 4.3 Abstände und Raster

- Erlaubte Abstände in dp: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Andere Werte gibt es nicht.
- Seitenrand 24 dp für Text, Listen und Boxen; die Boxkanten liegen auf einer Linie mit der Textkante.
- Hervorgehobene Listenzeilen (Heute im Wochenplan) ragen mit ihrer Fläche 12 dp über die Spalte hinaus. Ihr Inhalt bleibt auf der Spalte.
- Boxen: 16 dp Innenabstand, Elemente darin 4 bis 12 dp auseinander, Boxen untereinander 12 dp.
- Abschnitte 40 dp auseinander, vom Abschnittstitel bis zum Inhalt 12 dp.
- Kopfbereich: von der Statusleiste bis zum Titel 16 dp, vom Titel bis zur Unterzeile 4 dp, vom Kopfbereich bis zum Inhalt 24 dp.
- Rezeptraster: 2 Spalten, 16 dp zwischen den Spalten, 28 dp zwischen den Reihen.
- Karussells beginnen bei 24 dp und laufen rechts über den Bildschirmrand hinaus. Karten sind 148 dp breit, die dritte ist angeschnitten.
- Touch-Ziele mindestens 48 dp, auch wenn das sichtbare Element kleiner ist.
- Scrollende Inhalte enden 24 dp über jeder Leiste. Nichts wird dauerhaft verdeckt.
- Zielgeräte: Smartphones hochkant mit 360 bis 480 dp Breite. Im Querformat darf nichts kaputtgehen.

### 4.4 Formen

- Radien: Boxen 20 dp, Rezeptbilder in Karten 14 dp, Thumbnails 12 dp, Sheets oben 28 dp. Buttons, Pills, Suchfeld und Stepper sind voll gerundet. Das Hero-Foto geht bis an den Rand und hat keinen Radius.
- Bei Verschachtelung gilt: innerer Radius = äußerer Radius minus Abstand, mindestens 4 dp.

### 4.5 Flächen, Linien, Schatten

**Flächen** sind das wichtigste Mittel zur Gliederung. Kontrastregel: Auf `background` steht `surface`, innerhalb einer Box `surfaceInBox`. Buttons, Felder, Stepper, Karten und Boxen haben keine Rahmen.

**Linien:** Es gibt genau eine Linienart, `separator`. Sie ist 1 px stark, links bis zur Textkante eingerückt und steht nur zwischen den Zeilen gruppierter Listen in Boxen (Einstellungen, Einkauf). Sonst gibt es keine Linien: nicht unter Kopfleisten, nicht über Leisten, nicht zwischen Zutaten, Schritten, Rezeptzeilen oder Tagen, und keine Spaltentrenner. Wird eine Zuordnung ohne Linie unklar, hilft zuerst mehr Abstand, dann eine Fläche.

**Schatten:** Das sind Startwerte, die du nach Augenmaß feinjustierst. Angabe je Schicht: Versatz y / Weichzeichnung / Deckkraft, jeweils in der Farbe `shadow`.

| Ebene | Schichten | Sichtbar |
|---|---|---|
| Untere Leisten (Navigation, Aktionsleisten) | −1/3/5 % · −6/16/6 % · −16/40/6 % | immer |
| Eingeklappte Kopfleiste | 1/3/4 % · 6/16/5 % | nur wenn Inhalt darunter liegt (Einblendung 150 ms) |
| Sheet | −2/8/6 % · −12/40/10 % | immer |
| Menü, Snackbar | 2/6/6 % · 12/32/10 % | immer |

- **Abnahme für untere Leisten:** Auf dem Screenshot hebt sich die Leiste klar ab, ohne sichtbare Kante oder Linie. Der Schatten läuft über etwa 24 bis 32 dp weich nach oben aus und wirkt warm-bräunlich, nicht grau.
- **Dark Mode:** Leisten heben sich durch die hellere Fläche `bar` ab, die Schatten (Farbe `shadow`) bekommen die doppelte Deckkraft. Auch hier gibt es keine Linien.
- **Fotos** bekommen innen eine 1-px-Kontur in `ink` mit 6 % Deckkraft (dunkel: Weiß mit 6 %), damit helle Bilder nicht ins Papier übergehen.
- **Scrim** oben auf dem Hero-Foto: 120 dp hoch, `scrim` von 45 % auf 0 %. Die Icons der Statusleiste sind hell, solange das Foto darunter liegt.

### 4.6 Icons

- **Familie:** die bestehende, falls sie einheitlich ist, sonst Lucide. Strichstärke 1,5 dp bei 24 dp.
- **Größen:** 16 dp in Metazeilen; 20 dp in Buttons, Zeilen und Icon-Buttons; 24 dp in der Navigation.
- **Farben:** `inkSecondary` in Meta- und Listenzeilen, `ink` in Icon-Buttons. Das aktive Navigationsziel ist `ink`, als gefüllte Variante oder mit 2 dp Strich.
- **Flächenregel, ohne Ausnahme:** Ein einzeln stehendes Icon, das man antippen kann, ist ein Icon-Button und hat immer eine Kreisfläche. Sichtbar ist sie 40 dp groß, in Leisten 48 dp. Als Fläche dient `surface` bzw. `surfaceInBox`, auf Fotos `background` mit 90 % Deckkraft.
  Icons in beschrifteten Elementen haben nie eine eigene Fläche. Das betrifft Buttons mit Text, die Navigation, Listen- und Einstellungszeilen, Textaktionen und Metazeilen.
  Ein Auswahlzustand sieht überall gleich aus: eine weiche Pill in `accentSoft`.
- **Zuordnung:**
  - Vorbereitung: Besteck
  - Garzeit: Flamme
  - Gesamtzeit: Uhr
  - Ertrag: Gebäck oder Teller
  - Vegetarisch: Blatt
  - Quelle: Pfeil nach rechts oben
  - Einplanen: Kalender mit Plus
  - Einkaufen: Korb
  - Kochen starten: Topf
  - Merken: Lesezeichen
  - Suche: Lupe
  - Filter: Regler

### 4.7 Bewegung und Haptik

- **Dauern:** 150 ms für Kanten und Einblendungen, 250 ms für Zustandswechsel und die Auswahl-Pill, 350 ms für Wechsel zwischen Screens. Standard-Abbremskurve; Federn ohne sichtbares Nachschwingen.
- Beim Öffnen wächst das Rezeptfoto von der Karte ins Hero (Shared Element), der Titel wandert mit. Zurück geht es umgekehrt.
- Die Auswahl-Pill gleitet in Navigation und Kategorien zum neuen Ziel.
- Beim Drücken skalieren Karten und Boxen auf 0,98. Das Feedback beim Drücken ist eine dezente Tönung (`ink` mit 6 %), keine farbige Wellenanimation.
- Bilder zeigen erst einen Platzhalter in `surfacePressed` und werden dann in 200 ms eingeblendet.
- Haptisches Feedback beim Abhaken, beim Schrittwechsel im Kochmodus und beim Merken.
- Ist in den Systemeinstellungen reduzierte Bewegung aktiv, werden alle Übergänge zu kurzen Überblendungen.

## 5. Komponenten

Jede Komponente gibt es genau einmal.

1. **Kopfbereich (groß):** Titel `display`, darunter optional eine Unterzeile `subtitle`. Rechts bis zu zwei Icon-Buttons, vertikal mittig zur ersten Titelzeile. Hintergrund `background`, auch hinter der Statusleiste.
2. **Kopfleiste (eingeklappt):** 56 dp hoch, Fläche `bar` mit Schatten (siehe 4.5), sobald Inhalt darunter liegt. Titel `barTitle`, einzeilig. Beim Scrollen geht der große Kopfbereich in diese Leiste über.
3. **Navigationsleiste:** 64 dp plus Systemrand, Fläche `bar`, Schatten (siehe 4.5), keine Linie, vier Ziele.
   - Aktiv: Pill 56 × 32 dp in `accentSoft` hinter dem Icon, Icon `ink`, Label `nav` 500 in `ink`.
   - Inaktiv: Icon und Label in `inkSecondary`, Label 400.
   - Die Pill gleitet in 250 ms zum neuen Ziel.
4. **Buttons:**
   - Primär: Fläche `ink`, Text `onInk`, 48 dp hoch, Stil `label`, optional ein Icon mit 20 dp.
   - Sekundär: Tonfläche (`surface` bzw. `surfaceInBox`), Text `ink`, 48 dp hoch.
   - Textaktion: `accent`, ohne Fläche, optional mit Icon davor.
   - Kompakte Varianten mit 36 dp Höhe für Boxen.
   - Andere Button-Stile gibt es nicht, auch keine FABs.
5. **Icon-Button:** siehe Flächenregel in 4.6.
6. **Box:** `surface`, Radius 20, Innenabstand 16, ohne Rand und ohne Schatten. Optionale Kopfzeile: links der Titel in `label`, rechts `meta` und/oder ein Chevron.
7. **Metazeile:** Icon mit 16 dp plus `meta`. Mehrere Angaben werden mit „ · " getrennt.
8. **Rezeptkarte:**
   - Bild im Format 4 : 5, Radius 14, mit Innenkontur.
   - Name in `cardTitle`, die Höhe für 2 Zeilen ist reserviert, höchstens 3 Zeilen. Darunter die Metazeile (Zeit, ersatzweise Kategorie).
   - Gemerkte Rezepte tragen das Lesebändchen (12 × 36 dp) oben rechts im Bild.
   - Karten einer Rasterreihe sind gleich hoch, ihre Metazeilen stehen auf einer Linie.
9. **Rezeptzeile:** Thumbnail 56 dp mit Radius 12, Name in `cardTitle` (höchstens 2 Zeilen), darunter die Metazeile. 12 dp zwischen den Zeilen.
10. **Lesebändchen:**
    - Fläche `accent`, unten eine V-Kerbe mit 20 % der Höhe als Tiefe, oben bündig mit der Bildkante.
    - Größe im Hero 16 × 52 dp, auf Karten 12 × 36 dp.
    - Beim Merken gleitet es in 250 ms von oben herein, mit haptischem Feedback.
    - Es ersetzt das Herz überall.
11. **Kennzahlen-Box (Rezeptseite):** Box mit bis zu drei gleich breiten Spalten ohne Trenner. Jede Spalte enthält ein Icon (20 dp, `inkSecondary`), den Wert in `label` (`tnum`) und die Bezeichnung in `meta`. Fehlende Werte entfallen; fehlen alle, entfällt die Box.
12. **Stepper:** Pill 40 dp in `surface` mit „−", Wert (`label`) und „+". Jedes Touch-Ziel 48 dp.
13. **Auswahlreihe (Kategorien, Segmente):** Textsegmente im Stil `label`.
    - Ausgewählt: Pill 36 dp in `accentSoft`, Text `ink`.
    - Nicht ausgewählt: Text `inkSecondary` ohne Fläche.
    - Horizontal scrollbar; das erste Segment heißt „Alle".
14. **Suchfeld:** 48 dp, `surface`, voll gerundet, Lupe 20 dp in `inkSecondary`, Platzhalter „Rezepte oder Zutaten suchen".
15. **Abhak-Kreis:** 24 dp, mit haptischem Feedback.
    - Offen: Tonfläche mit 1,5-dp-Ring in `inkTertiary`.
    - Erledigt: `accent` mit Haken in `onInk`. Die Zeile wird `inkTertiary`, im Einkauf zusätzlich durchgestrichen.
16. **Leerzustand:** eine Zeile `bodySmall` plus Textaktion, keine Illustration, höchstens 56 dp hoch.
17. **Aktionsleiste unten:** 72 dp plus Systemrand, Fläche `bar`, Schatten (siehe 4.5), 16 dp Innenabstand. Links stehen Icon-Buttons (48 dp), der Primär-Button füllt den Rest.
18. **Einstellungszeile:** Icon 20 dp in `inkSecondary`, Titel in `body`, optional eine Beschreibung in `bodySmall`. Rechts steht der Wert (`meta`) mit Chevron oder ein Schalter. Die Zeilen stehen in einer Box, dazwischen liegt `separator`.
19. **Schalter:** An: Spur `accent`, Knopf `onInk`. Aus: Spur `surfacePressed`, Knopf `inkTertiary`. Kein Rand.
20. **Sheet, Dialog, Snackbar:** Fläche `bar`.
    - Sheets haben oben einen Radius von 28 und einen Griff von 32 × 4 dp in `surfacePressed`.
    - Snackbar: Fläche `ink`, Text `onInk`, Aktion in `accentSoft`.
    - In Auswahllisten trägt die gewählte Option einen Haken in `accent`.
21. **Skeleton:** Flächen in `surfacePressed` in der Form des späteren Inhalts, sanftes Pulsieren (60 bis 100 % Deckkraft, 1,2 s). Lade-Kreisel gibt es nur in Buttons, während eine Aktion läuft.

## 6. Screens

### 6.1 Rezeptseite (Referenz)

Von oben nach unten:

1. **Hero:** Foto bis an den Rand im Format 4 : 3, Scrim oben.
   - Icon-Buttons: links Zurück, rechts Merken und Menü (⋯).
   - Ist das Rezept gemerkt, verschwindet der Merken-Button. An seiner Stelle hängt das Lesebändchen von der Oberkante herab.
   - Tippen auf das Bändchen hebt das Merken auf; eine Snackbar bietet „Rückgängig" an.
2. **Titelblock**, 24 dp unter dem Foto: Titel in `recipeTitle`.
   - Darunter die Unterzeile `subtitle` mit Kategorie, bis zu zwei Tags und der Quelle. Die Quelle steht als Domain ohne „www" mit ↗, in `accent` und antippbar.
   - Sterne nur, wenn eine Bewertung existiert: eigene Zeile, 14 dp, `accent`, die Zahl in `meta`.
3. **Kopfnote**, 16 dp darunter: `headnote`.
4. **Kennzahlen-Box**, 24 dp darunter: Vorbereitung · Garzeit · Gesamt. Die Garzeit heißt bei Backrezepten „Backen", sonst „Kochen".
5. **Zutaten**, 40 dp darunter:
   - Titel in `section`. Rechts der Stepper mit der Einheit aus dem Ertrag („1 Blech", „4 Portionen"; ohne Ertragsangabe „Portionen").
   - Darunter eine `meta`-Zeile mit dem Ertrag („ca. 24 Stück") und der Textaktion „Einheiten: wie im Rezept", die ein Sheet öffnet.
   - Gruppen bekommen ein `groupLabel`, mit 24 dp Abstand davor und 8 dp danach.
   - Zeilen ohne Linien und ohne Boxen, mindestens 40 dp hoch.
   - Die Mengenspalte ist rechtsbündig, so breit wie die breiteste Menge, mindestens 56 dp, in `accent` und `tnum`. Dann folgen 16 dp Abstand und der Name in `body`.
   - Zutaten ohne Menge stehen in der Namensspalte.
6. **Zubereitung**, 40 dp darunter:
   - Titel in `section`.
   - Je Schritt links die Ziffer in `numeral` und `inkSecondary` (Spalte 40 dp), rechts der Text in `body`. 24 dp zwischen den Schritten.
   - Die Zutaten eines Schritts erscheinen je nach Einstellung als `meta` unter dem Text.
7. **Weitere Abschnitte** (Notizen, Nährwerte), soweit vorhanden, stehen offen mit einem Titel in `section`.
8. **Aktionsleiste:** Einplanen und Einkaufen als Icon-Buttons, „Jetzt kochen" mit Topf-Icon als Primär-Button.
9. **Kopfleiste:** Sobald das Hero aus dem Bild gescrollt ist, erscheint die eingeklappte Kopfleiste mit dem Rezeptnamen.
10. **Verwaltung:** Art des Gerichts, Tags, Bewertung setzen, Bild, Bearbeiten, Teilen und Löschen sind nur über ⋯ bzw. den Bearbeiten-Modus erreichbar.

**Akzeptanz:**
- genau ein Primär-Button
- keine einzige Linie
- keine Sterne ohne Bewertung
- die Statusleiste ist auf hellem wie dunklem Foto lesbar
- der letzte Inhalt endet 24 dp über der Aktionsleiste
- alle Testtexte brechen korrekt um

### 6.2 Startseite

1. **Kopf:** „Guten Morgen/Tag/Abend, {Vorname}" (5 bis 11 Uhr / 11 bis 18 Uhr / ab 18 Uhr), Unterzeile „Dienstag, 24. November".
   - Icon-Buttons: Suche und Profil (Kreis mit Initiale im Stil `label`).
   - Auf Start gibt es kein Suchfeld.
2. **Box „Heute":**
   - Bild im Format 16 : 10 oben, seine oberen Ecken folgen der Box.
   - Darunter eine Kopfzeile: links „Heute Abend" (die Mahlzeit aus dem Plan, `label` in `inkSecondary`), rechts die Zeit als Metazeile.
   - Dann der Rezeptname in `section` (höchstens 2 Zeilen), darunter „Jetzt kochen" (Primär, kompakt) und „Rezept" (Sekundär, kompakt).
   - Weitere Mahlzeiten von heute folgen als Rezeptzeilen.
   - Ist nichts geplant, heißt die Box „Für heute ist noch nichts geplant". Sie zeigt dann drei Vorschläge als Rezeptzeilen, jeweils mit der Textaktion „Einplanen".
3. **Box „Diese Woche":**
   - Kopfzeile „Diese Woche", rechts „2 von 7 geplant" mit Chevron.
   - Darunter sieben Spalten: Kürzel in `meta`, Ziffer in Serif 17 sp.
   - Die Ziffer von heute steht in einem 32-dp-Kreis in `ink`, mit Schrift in `onInk`. Geplante Tage tragen einen 4-dp-Punkt in `accent`, vergangene Tage sind `inkTertiary`.
   - Tippen öffnet den Wochenplan an diesem Tag.
4. **Box „Einkaufsliste"**, nur wenn Artikel offen sind: Icon-Kreis in `surfaceInBox` mit Korb, „Einkaufsliste" in `label`, „12 Artikel offen" in `meta`, Chevron.
5. **Inspiration**, 40 dp darunter, offen und ohne Boxen, in wechselnden Formaten:
   - „Ideen für heute" (`section`) mit der Textaktion „Alle": Karussell aus Rezeptkarten.
   - „Schnell gemacht" (`section`): drei Rezeptzeilen mit höchstens 30 Min.
   - „Neu in deiner Sammlung" (`section`): ein einzelnes Rezept groß. Bild im Format 4 : 3 über die volle Inhaltsbreite (Radius 14), Name in `section`, zwei Zeilen Kopfnote und eine Metazeile.

Regeln für die Inspiration:

- Kein Rezept erscheint zweimal auf der Seite; der obere Abschnitt hat Vorrang.
- Ein Abschnitt entfällt, wenn er weniger als drei passende Rezepte hat. Das große Einzelrezept braucht nur eines.
- Die Auswahl für „Ideen für heute" bleibt den ganzen Tag gleich.

**Akzeptanz:** Auf 412 × 915 dp sind ohne Scrollen der Kopf, die vollständige Heute-Box und der Anfang von „Diese Woche" zu sehen.

### 6.3 Wochenplan (Kalenderwoche Mo–So)

- **Kopf:** „Wochenplan" mit der Unterzeile „23.–29. November · KW 48", über eine Monatsgrenze „30. Nov.–6. Dez. · KW 49".
  - Rechts der Wochenwechsler: Pill 40 dp in `surface` mit ‹ und ›, jeweils mit 48 dp Touch-Ziel. Auch Wischen wechselt die Woche.
  - Außerhalb der aktuellen Woche steht in der Unterzeile zusätzlich die Textaktion „Zur aktuellen Woche".
- **Agenda:** ohne Linien, 8 dp zwischen den Tagen.
  - Linke Spalte, 44 dp breit: Kürzel in `meta` über der Ziffer in `numeral` und `inkSecondary`.
  - Rechte Spalte bei geplanten Tagen: Rezeptzeile mit der Metazeile „Abendessen · 45 Min.". Mehrere Mahlzeiten stehen mit 8 dp Abstand untereinander. Rechts ein Icon-Button mit Plus für eine weitere Mahlzeit.
  - Rechte Spalte bei leeren Tagen: die Textaktion „Gericht planen" mit Plus, Zeilenhöhe 56 dp.
- **Heute:** Die Zeile liegt auf `surface` (Radius 20) und ragt 12 dp über die Spalte hinaus. Statt des Kürzels steht „Heute" in `accent` (500), die Ziffer ist `ink`.
- **Vergangene Tage:** `inkTertiary`, Thumbnails mit 60 % Deckkraft. Ab zwei vergangenen Tagen fasst eine aufklappbare Zeile sie zusammen („Mo–Mi · 2 Gerichte").
- **Box „Zutaten der Woche"** am Ende: Korb im Icon-Kreis, „Zutaten der Woche" in `label`, „aus 2 Gerichten · 14 Artikel" in `meta` und „Auf die Liste" (Sekundär, kompakt). Die Box ersetzt das unbeschriftete Korb-Icon im Kopf.
- Tippen auf ein Gericht öffnet das Rezept. Langes Drücken öffnet ein Sheet mit den vorhandenen Planaktionen.

**Akzeptanz:**
- In jeder Zeile ist das Gericht das stärkste Element. Die Tagesziffer dient der Orientierung und ist keine Überschrift.
- Leere Tage sind nie höher als 56 dp.

### 6.4 Rezepte

- **Kopf:** „Rezepte" mit der Unterzeile „{n} Rezepte". Icon-Buttons: Filter und Hinzufügen. Bei aktivem Filter sitzt ein 8-dp-Punkt in `accent` am Filter-Kreis. Kein FAB.
- Das Suchfeld steht 16 dp unter dem Kopf, die Auswahlreihe der Kategorien 12 dp darunter.
- Darunter das Raster aus Rezeptkarten (siehe 4.3).
- Findet die Suche nichts oder ist eine Kategorie leer, erscheint ein Leerzustand mit der Textaktion „Filter zurücksetzen".

### 6.5 Kochmodus

- **Kopfleiste**, 56 dp: links Schließen als Icon-Button, rechts die Textaktion „Zutaten". Sie öffnet ein Sheet mit allen Zutaten zum Abhaken. Kein Rezepttitel.
- **Fortschritt:** ein Segment pro Schritt, 3 dp hoch, 4 dp Abstand, voll gerundet. Erledigte Schritte und der aktuelle sind `accent`, offene `surfacePressed`.
- **Schrittziffer**, 24 dp darunter: die Ziffer in `numeralLarge` und „von 6" in `meta`, beide auf derselben Grundlinie.
- **Schritttext** in `cookStep`. Mengen im Text bleiben wie bisher in `accent`.
- **Zutaten des Schritts:** eine Box, nur so hoch wie ihr Inhalt. Kopfzeile „Für diesen Schritt" mit „0 von 2" rechts. Zeilen mit Abhak-Kreis, Menge (`accent`, `tnum`) und Name.
- **„Als Nächstes"**, 24 dp darunter: die Überschrift in `label` und `inkSecondary`, darunter die ersten zwei Zeilen des nächsten Schritts in `body` und `inkSecondary`. Beim letzten Schritt entfällt der Block.
- **Aktionsleiste:** Zurück als Icon-Button und „Weiter" als Primär-Button, beide 56 dp für größere Ziele in der Küche. Im letzten Schritt heißt der Button „Fertig".
- Wischen wechselt den Schritt, mit haptischem Feedback. Der Bildschirm bleibt an (bestehende Einstellung).

### 6.6 Einkauf

Hier gibt es weniger Vorgaben; wende das System konsequent an.

- **Kopf:** „Einkauf" mit der Unterzeile „12 offen". Icon-Buttons: Hinzufügen und Menü.
- **Gruppen**, sofern es Kategorien oder Labels gibt: Der Gruppenkopf steht in `label` und `inkSecondary` 24 dp über seiner Box. Jede Gruppe ist eine Box mit Zeilen (Abhak-Kreis, Menge in `accent` und `tnum`, Name in `body`), dazwischen `separator`.
- Abgehakte Artikel wandern in die eingeklappte Gruppe „Erledigt (n)".
- Ist die Liste leer, erscheint ein Leerzustand mit der Textaktion „Zutaten aus dem Wochenplan hinzufügen".

### 6.7 Einstellungen

- **Kopf:** „Einstellungen".
- **Profil-Box:**
  - Kreis 56 dp in `surfaceInBox` mit Initiale (Sans 500, 22 sp).
  - Name in `labelLarge` (Sans, nicht Serif), E-Mail in `meta`.
  - Server: 8-dp-Punkt in `accent` und „Verbunden · Mealie 3.31.0" in `meta`, darunter die Adresse in `inkTertiary`.
- **Gruppen:** Gruppenkopf in `label` und `inkSecondary`, darunter eine Box mit Einstellungszeilen. Auswahlen öffnen ein Sheet statt eines Dropdowns.
- „Farben vom Hintergrundbild" ist standardmäßig aus. Beschreibung: „Färbt Akzente passend zu deinem Hintergrundbild."

### 6.8 Alle übrigen Screens

Rezept hinzufügen und Import, Bearbeiten, Filter-Sheet, Suche (Ergebnisse als Rezeptzeilen), Dialoge sowie Lade- und Fehlerzustände folgen denselben Tokens, Komponenten und Regeln. Ein Fehler erscheint als eine Zeile mit der Textaktion „Erneut versuchen". Der Bearbeiten-Modus nimmt alles auf, was aus der Leseansicht verschwunden ist.

## 7. Anzeige der Daten

- **Zeiten** aus ISO-8601 (PT1H20M) und Freitext vereinheitlichen:
  - unter 60 Minuten: „45 Min."
  - ab 60 Minuten: „1 Std. 20 Min." bzw. „2 Std."
  - Spannen: „10–15 Min."
  - Was nicht erkannt wird, bleibt im Original stehen.
  - Fehlt die Gesamtzeit, sind aber beide Teilzeiten da, ist die Gesamtzeit deren Summe.
- **Ertrag** kommt aus dem Rezept („1 Blech", „4 Portionen", „ca. 24 Stück"). Der Stepper skaliert Mengen und Ertrag.
- **Quelle:** Domain ohne Protokoll und ohne „www".
- **Datum:** „Dienstag, 24. November"; Kürzel „Mo" bis „So"; Spannen wie in 4.2.
- **Fehlende Daten** erzeugen keine Platzhalter wie „—", „0 Min." oder leere Sterne. Das Element entfällt dann.

## 8. Fehler (Phase 1)

1. **Umbruch:** „Zitronen-Baiser-Pl / ätzchen", „Aubergi / nen-Tikka-Masala" und „BBQ / -Hähnchen" mit dem Bindestrich am Zeilenanfang. Vermutlich gelten die Bindestriche nicht als Umbruchstelle, und die Silbentrennung ist aus. Finde die Ursache und behebe sie überall nach den Regeln in 4.2. In den Einstellungen funktioniert die Trennung bereits („Rezept-seite").
2. **Statusleiste:** Beim Scrollen läuft Inhalt sichtbar unter Uhrzeit und Icons durch (Start: „Ideen für heute"). Hinter der Statusleiste liegt immer `background` oder ein Foto mit Scrim.
3. **Hero ohne Scrim:** Auf hellen Fotos ist die Statusleiste nicht lesbar.
4. **Verdeckungen:** Der FAB „Jetzt kochen" verdeckt den Mengen-Stepper, der FAB „Rezept hinzufügen" einen Rezepttitel. Alle FABs fallen weg; ihre Aktionen wandern in den Kopf bzw. die Aktionsleiste.
5. **Kochmodus:** Die Zutaten-Box ist auf volle Höhe gestreckt.
6. **Uneinheitliche Metadaten** („30 Min.", „10–15 Minuten", „★ 4,0", leer): siehe Abschnitt 7.
7. **Leere Sterne** bei Rezepten ohne Bewertung.
8. **Dropdown ohne Beschriftung** („Wie im Rezept") auf der Rezeptseite.

## 9. Grenzen

- Keine Änderungen an API-Verträgen, Datenmodellen, Sync oder Anmeldung. Neue Anzeigen nur aus vorhandenen Daten.
- Nicht Teil dieser Runde: Bewertung nach dem Kochen, Timer aus dem Schritttext, Saison-Modul, Bildausschnitt beim Import und andere neue Funktionen.
- Neue Abhängigkeiten nur für Schriftdateien und ein Screenshot-Werkzeug im Test-Scope.
- **Barrierefreiheit:**
  - Kontraste wie in 4.1. Icons und Flächen, die etwas bedeuten, haben mindestens 3 : 1.
  - Jedes Element ohne Text hat eine Beschreibung für TalkBack.
  - Bei 200 % Schriftgröße wird nichts abgeschnitten und nichts überlappt; die Layouts wachsen mit.

## 10. Definition of Done

- [ ] Alle Screens und Zustände sind gerendert, angesehen und gegen ihre Akzeptanzkriterien geprüft.
- [ ] Die Testtexte aus Abschnitt 14 brechen überall korrekt um.
- [ ] Keine Labels in Großbuchstaben und keine erweiterte Laufweite (per Code-Suche geprüft).
- [ ] Linien nur als `separator` in gruppierten Listen (per Suche nach Dividern und Rahmen geprüft).
- [ ] Keine Flächen in #FFFFFF, #EFEEEB oder #D6E7DA, keine Mint-Container, keine FABs.
- [ ] Keine Farb-, Schriftgrößen- oder Abstandswerte außerhalb des Design-Systems.
- [ ] Die Serif erscheint nur in den Rollen aus 4.2.
- [ ] Jeder Screen hat genau einen Primär-Button.
- [ ] Jedes einzeln stehende Icon, das man antippen kann, hat eine Fläche. Kein anderes Icon hat eine.
- [ ] Untere Leisten haben einen sichtbaren, warmen, weichen Schatten und keine Linie.
- [ ] Nichts verdeckt Inhalte, nichts läuft unter die Statusleiste.
- [ ] Das aktive Navigationsziel ist hell wie dunkel auf einen Blick erkennbar.
- [ ] Der Dark Mode ist warm und ersetzt Schatten nicht durch Linien.
- [ ] Material You ist standardmäßig aus; eingeschaltet färbt es nur den Akzent.
- [ ] Build, Lint und Tests sind grün, `PROGRESS.md` ist vollständig.
- [ ] Der Draft des nächsten Releases ist angelegt und nicht veröffentlicht. Der PR ist offen, beide Links sind ausgegeben.

## 11. Abschluss

1. **Art-Director-Review:** Geh alle Screenshots mit den Augen eines sehr anspruchsvollen Art Directors durch. Suche nach überflüssigen Linien und Flächen, nach Abständen außerhalb der festgelegten Werte, nach Kollisionen, schlechten Umbrüchen und uneinheitlichen Komponenten, und nach allem, was nach Template aussieht. Korrigiere, rendere neu und prüfe noch einmal.
2. **Bericht** in `docs/design/BERICHT.md` mit diesen Inhalten:
   - Änderungen pro Phase
   - wohin verschobene Funktionen gewandert sind
   - Abweichungen von diesem Auftrag, mit Begründung
   - offene Punkte
   - Vorher-Nachher-Bilder von Start, Rezepte, Rezeptseite, Kochmodus, Wochenplan und Einstellungen, jeweils hell und dunkel

## 12. Release-Draft (letzter Schritt)

- Folge dem bestehenden Release-Prozess des Repos. Nimm bisherige Releases, Versionierung, Changelog, Signierung und Workflows als Vorbild.
- Erhöhe die Version auf das nächste Minor-Release.
- Pushe den Branch `design/kochbuch` und öffne einen PR in den Hauptbranch. Merge ihn nicht.
- Baue das Release so wie bisher, per CI oder lokal mit der vorhandenen Signatur. Lege es als **Draft** an, mit installierbarem APK und kurzen Release-Notes für Nutzer im Stil der bisherigen.
- Nichts darf automatisch veröffentlicht werden. Löst ein Tag-Push einen Workflow aus, der direkt veröffentlicht, sorge dafür, dass trotzdem nur ein Draft entsteht.
- Scheitert ein Schritt an fehlenden Rechten (z. B. `gh` nicht angemeldet), nenne mir genau den Befehl, den ich ausführen muss.
- **Deine letzte Nachricht:** der Link zum Draft-Release und der Link zum PR, darunter drei Sätze dazu, was sich geändert hat.

## 13. Technische Hinweise

Diese Hinweise gelten für Jetpack Compose. Bei einem anderen Stack setze sie sinngemäß um.

- **Theme:** Lege ein eigenes `AromaTheme` an, mit CompositionLocals für Farben, Typografie, Abstände, Formen und Schatten. Das Material-`ColorScheme` wird nur als Fallback auf die Tokens abgebildet, damit Material-Komponenten nicht auf die alten Rollen zurückfallen.
- **Umbruch:** `TextStyle(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading)` für Titel, `LineBreak.Paragraph` für Fließtext, `localeList = LocaleList("de-DE")`. Enthält ein Text U+2011 (Bindestrich ohne Umbruchmöglichkeit), ersetze ihn durch „-".
- **Ziffern:** `fontFeatureSettings = "tnum"`.
- **Schatten:** Verkette mehrere `Modifier.dropShadow()` statt `Modifier.shadow()` zu nutzen. Ob der Schatten der Kopfleiste sichtbar ist, hängt an `canScrollBackward`.
- **Statusleiste:** Edge-to-Edge mit `WindowInsets`. Die Icons der Statusleiste wechseln zwischen hell und dunkel, je nachdem, ob das Hero zu sehen ist.
- **Übergänge:** `SharedTransitionLayout` mit `sharedElement` für Foto und Titel.
- **Lesebändchen:** als eigene `Shape` (Rechteck mit V-Kerbe).

## 14. Testtexte und Testdaten

- **Titel:**
  - „Zitronen-Baiser-Plätzchen vom Blech"
  - „Cremiges Auberginen-Tikka-Masala mit Kokos-Joghurt"
  - „Koreanische BBQ-Hähnchen-Wraps"
  - „Kürbis-Couscous-Taler mit Feta-Füllung"
  - „No-Bake Bienenstich-Tiramisu"
  - „Rhabarber-Erdbeer-Streuselkuchenschnitten"
  - „Ei"
- **Zeiten:** „PT1H20M", „10–15 Minuten", „45 min", „ca. 2 Std.", leer
- **Zutaten:** „Abrieb von 1–2 Bio-Zitronen" (ohne Menge), „Salz", 0,5 TL (wird zu „½ TL"), „1 Prise"
- **Fälle:**
  - eine Woche über eine Monatsgrenze (30. Nov.–6. Dez.)
  - ein Rezept ohne Bild, Zeiten, Beschreibung und Bewertung
  - ein Rezept mit 30 Zutaten in 4 Gruppen
  - eine leere Einkaufsliste und eine mit 40 Artikeln
