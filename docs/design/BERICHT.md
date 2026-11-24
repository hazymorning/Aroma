# Bericht – Design-Runde „Kochbuch"

Aroma sieht jetzt aus wie ein gut gesetztes Kochbuch im Zweifarbdruck: Papier, Tinte und Salbei,
eine Serif für Rezepte und Überschriften, eine ruhige Sans für alles, was man bedient. Alle Screens,
Sheets, Dialoge und Zustände sind neu aufgebaut, hell und dunkel, mit denselben wenigen Bausteinen.
Der Auftrag steht in [`AUFTRAG.md`](AUFTRAG.md), der Verlauf mit allen Einzelheiten und
Entscheidungen in [`PROGRESS.md`](PROGRESS.md).

Branch `design/kochbuch`, Phasen P0 bis P8 je mit eigenem Commit. Build, Lint (Warnungen als
Fehler), Spotless und 847 Tests sind grün; 18 davon laufen nur gegen einen echten Mealie-Server und
sind in der CI des Pull Requests grün.

## Vorher – Nachher

Gerendert mit Roborazzi auf 411 × 891 dp, Deutsch. Im Repository liegen nur diese Paare; alle
Screens, Sheets und Widgets rendert bei Bedarf der Workflow „Screenshots" (oder
`./gradlew :app:recordRoborazziDebug -Pscreenshots`).

| | Vorher | Nachher |
|---|---|---|
| **Start**, hell | <img src="screenshots/vorher/home_light_de.webp" width="260"> | <img src="screenshots/nachher/home_light_de.webp" width="260"> |
| **Start**, dunkel | <img src="screenshots/vorher/home_dark_de.webp" width="260"> | <img src="screenshots/nachher/home_dark_de.webp" width="260"> |
| **Rezepte**, hell | <img src="screenshots/vorher/recipes_light_de.webp" width="260"> | <img src="screenshots/nachher/recipes_light_de.webp" width="260"> |
| **Rezepte**, dunkel | <img src="screenshots/vorher/recipes_dark_de.webp" width="260"> | <img src="screenshots/nachher/recipes_dark_de.webp" width="260"> |
| **Rezeptseite**, hell | <img src="screenshots/vorher/recipe_light_de.webp" width="260"> | <img src="screenshots/nachher/recipe_light_de.webp" width="260"> |
| **Rezeptseite**, dunkel | <img src="screenshots/vorher/recipe_dark_de.webp" width="260"> | <img src="screenshots/nachher/recipe_dark_de.webp" width="260"> |
| **Kochmodus**, hell | <img src="screenshots/vorher/cook_light_de.webp" width="260"> | <img src="screenshots/nachher/cook_light_de.webp" width="260"> |
| **Kochmodus**, dunkel | <img src="screenshots/vorher/cook_dark_de.webp" width="260"> | <img src="screenshots/nachher/cook_dark_de.webp" width="260"> |
| **Wochenplan**, hell | <img src="screenshots/vorher/mealplan_light_de.webp" width="260"> | <img src="screenshots/nachher/mealplan_light_de.webp" width="260"> |
| **Wochenplan**, dunkel | <img src="screenshots/vorher/mealplan_dark_de.webp" width="260"> | <img src="screenshots/nachher/mealplan_dark_de.webp" width="260"> |
| **Einstellungen**, hell | <img src="screenshots/vorher/settings_light_de.webp" width="260"> | <img src="screenshots/nachher/settings_light_de.webp" width="260"> |
| **Einstellungen**, dunkel | <img src="screenshots/vorher/settings_dark_de.webp" width="260"> | <img src="screenshots/nachher/settings_dark_de.webp" width="260"> |

## Änderungen pro Phase

**P0 – Bestand.** Inventar aller Screens, Sheets, Dialoge, Zustände und Komponenten; 308
Vorher-Bilder (Deutsch und Englisch, hell und dunkel, Widgets).

**P1 – Fehler aus Abschnitt 8.**

- Titel brachen mitten im Wort ohne Trennstrich („Pl / ätzchen"): Ursache war ausgeglichener
  Umbruch ohne Silbentrennung, dazu trennt Android keine Wörter mit Bindestrich. Jetzt trennen alle
  Rollen automatisch, und eine eigene `Text`-Komponente gibt jedes Teilwort eines Bindestrich-Worts
  einzeln zur Trennung frei.
- Nichts läuft mehr unter der Statusleiste durch; über dem Hero sind ihre Icons hell, der Scrim hat
  120 dp von 45 % auf 0 %.
- Keine FABs mehr: Rezeptseite mit Aktionsleiste, „Rezept hinzufügen" als Plus im Kopf.
- Kochmodus ohne das 60-%-Zutaten-Panel: eine Box im Schritt, nur so hoch wie ihr Inhalt.
- Zeiten lesen ISO 8601 und Freitext und zeigen „1 Std. 20 Min." oder „10–15 Min."; fehlende
  Gesamtzeiten werden summiert, „0 Min." entfällt.
- Sterne nur bei einer Bewertung; Maßeinheiten als beschriftete Textaktion.

**P2 – Grundlagen und Komponenten.** Eigenes `AromaTheme` mit Farben, Typografie, Abständen, Formen
und Schatten als Tokens; Newsreader und Instrument Sans; Drücken tönt mit Tinte 6 % und gibt auf
0,98 nach, ohne Ripple. Jede Komponente gibt es genau einmal; die alten Varianten sind gelöscht
(Gegenüberstellung in `PROGRESS.md`).
`DesignRulesTest` sichert Tokens, Skala, Farben, Linien, FABs, Großbuchstaben, Laufweite und
Kontraste per Code-Suche.

**P3 – Rezeptseite** als Referenz: Hero 4 : 3 mit Lesebändchen, Titelblock mit Quelle als
eigenem Link, Kopfnote, Kennzahlen-Box (Vorbereitung, Kochen oder Backen, Gesamt), Zutaten mit
rechtsbündiger Mengenspalte in Salbei und Ertrag aus `recipeYield`, Schritte mit Serif-Ziffern,
Notizen, Nährwerte, Aktionsleiste. Verwaltung nur im Menü ⋯.

**P4 – Start und Wochenplan.** Start mit Gruß in zwei Zeilen, Heute-Box (oder drei Vorschläge zum
Einplanen), Wochenleiste, Einkaufsliste und Inspiration in drei Formaten ohne Doppelte. Wochenplan
als Kalenderwoche mit Wochenwechsler und Wischen, Agenda ohne Linien, Heute auf eigener Fläche,
vergangene Tage gefaltet, Box „Zutaten der Woche".

**P5 – Übrige Screens.** Rezepte (Suche als Zeilen), Einkauf, Einstellungen, Anmeldung, Rezept
hinzufügen, Bearbeiten, Sammlungen, Lizenzen, alle Sheets und Zustände. Auf Rückmeldung mehr Luft:
Navigationsleiste 80 dp mit 16 dp über der Pill, Aktionsleisten mit 16 dp ringsum, 16 dp zwischen
Zurück-Zeile und Titel, im Wochenplan 24 dp zwischen den Tagen. Einstellungszeilen mit
Beschreibung über die volle Breite; alle Screens zusätzlich bei 130 % Schrift gerendert.

**P6 – Bewegung, Dark Mode, Barrierefreiheit.** Screen-Wechsel als ruhige Überblendungen, bei
abgeschalteten Animationen ohne Gleiten. Jeder Screenshot-Test prüft, dass alles Antippbare einen
Namen für TalkBack hat; drei Lücken geschlossen. Bedeutungstragende Formen haben 3 : 1. Bei 200 %
bricht alles um, nichts wird abgeschnitten. (Das Shared Element aus dieser Phase ist nach der
Rückmeldung zum ersten Draft wieder entfernt, siehe unten.)

**P7 – Art-Director-Review.** Alle Screens in doppelter Größe mit gemessenen Abständen
durchgesehen, die Hauptscreens zusätzlich auf 360 und 480 dp Breite. Kurze Wörter werden nicht mehr
getrennt („ei-nem", „Sa-lat"), Zutatengruppen stehen näher an ihren Zutaten als an der Zeile davor,
die Wochen-Unterzeile hat eine Schriftgröße. Auf 360 dp stehen morgens Suche und Profil über dem
Gruß, der so zwei Zeilen behält, und „Einplanen" rückt unter die Metazeile, wenn der Name sonst zu
schmal würde. Eine ungenutzte Abfrage und 96 unbenutzte Imports sind entfernt.

**Nachbesserung nach dem ersten Draft.** Das Shared Element (Foto und Name wuchsen ins Hero) wirkte
auf dem Gerät unruhig und langsam und ist entfernt: Screens wechseln in 220 ms mit einer schlichten
Überblendung und wenigen dp Gleiten, nichts zoomt. Auswahl-Pills beginnen bündig an der Seitenkante,
der Heute-Tag im Wochenplan hat 16 dp Innenabstand oben und unten, unter Eyebrows wie „Wie hat es
geschmeckt?" stehen 8 dp, und die Snackbar zeigt ihren Text wieder in vollem Kontrast (die
Material-Typografie brachte die zweite Tinte mit).

**P8 – Release.** Version 0.7.0 (versionCode 9), Changelog-Eintrag, Pull Request in den
Hauptbranch, auf Wunsch gemergt. Der Release-Workflow legt jetzt standardmäßig nur einen Draft an
(ohne Tag, für die Update-Prüfung der App unsichtbar), ein erneuter Lauf ersetzt den Draft derselben
Version; veröffentlicht wird von Hand auf der Release-Seite. Der Draft für 0.7.0 ist von `main`
gebaut und trägt das signierte APK.

## Wohin verschobene Funktionen gewandert sind

| Vorher | Jetzt |
|---|---|
| Rezeptseite: Herz im Foto | Merken-Button im Foto; gemerkt hängt das Lesebändchen, ein Tippen darauf hebt das Merken auf; auf Karten das kleine Bändchen |
| Rezeptseite: Mint-Buttons Einplanen und Einkaufen, FAB „Jetzt kochen" | Aktionsleiste unten: Einplanen und Einkaufen als Icon-Buttons, „Jetzt kochen" als Primär-Button |
| Rezeptseite: Kategorie-Chips mit Stift | Menü ⋯ → „Kategorien und Schlagwörter" (mit der Art des Gerichts); in der Unterzeile stehen sie als Angabe |
| Rezeptseite: Sterne zum Bewerten | Menü ⋯ → „Rezept bewerten"; Sterne erscheinen nur, wenn es eine Bewertung gibt |
| Rezeptseite: Maßeinheiten-Dropdown ohne Beschriftung | Textaktion „Einheiten: wie im Rezept" unter dem Zutaten-Titel, öffnet ein Sheet |
| Rezeptseite: Bild ändern | Bearbeiten |
| Rezeptseite: Sammlung über Kategorie- oder Tag-Chip öffnen | Rezepte → Filter → Kategorien bzw. Schlagwörter |
| Start: Suchfeld | Lupe im Kopf; öffnet Rezepte mit fokussierter Suche |
| Start: „Favoriten" | Rezepte → Filter „Gemerkt" |
| Start: „Neu hinzugefügt" | „Neu in deiner Sammlung" (ein Rezept groß) und Rezepte, Sortierung „Neueste" |
| Start: „In 30 Minuten fertig" | „Schnell gemacht" und Rezepte → Filter „Schnell" |
| Start: „Lange nicht gekocht" | Rezepte → Filter „Lange nicht gekocht" (dieselbe Abfrage wie vorher) |
| Rezepte: Sortier-Icon und Sortier-Sheet | Filter-Sheet, Abschnitt Sortierung |
| Rezepte: Kochbuch-Karussell | Filter-Sheet, Kochbücher |
| Rezepte: Filter-Chips Schnell und Favoriten | Filter-Sheet „Eingrenzen" (Schnell, Gemerkt, Lange nicht gekocht); ein Punkt am Filter-Kreis zeigt aktive Filter |
| Rezepte: FAB „Rezept hinzufügen" | Plus im Kopf |
| Wochenplan: Korb-Icon „Woche einkaufen" | Box „Zutaten der Woche" am Ende mit „Auf die Liste" |
| Wochenplan: Wischen zum Entfernen | Lange drücken → Sheet „Entfernen"; TalkBack hat Einkaufen und Entfernen direkt an der Zeile. Wischen wechselt jetzt die Woche |
| Wochenplan: sieben Tage ab heute | Kalenderwoche Montag bis Sonntag, „Zur aktuellen Woche" in der Unterzeile |
| Einkauf: Einkaufsmodus und Listenwechsel im Kopf | Menü ⋯ (mit Liste anlegen, umbenennen, löschen, Erledigte entfernen) |
| Einkauf: „Im Korb" mit „Leeren" | Eingeklappte Gruppe „Erledigt (n)"; Entfernen im Menü ⋯ |
| Kochmodus: aufziehbares Zutaten-Panel | Box „Für diesen Schritt" im Schritt und „Zutaten" oben rechts (Sheet mit allen Zutaten) |
| Einstellungen: Dropdowns | Sheets mit Haken an der gewählten Option |

## Abweichungen vom Auftrag

1. **Icon-Buttons in der Kopfleiste 40 statt 48 dp:** In der 56-dp-Leiste blieben einem 48-dp-Kreis
   nur 4 dp Luft, und die Kreise sprängen beim Einklappen. Die Touch-Ziele bleiben 48 dp.
2. **Zutatenzeilen auf der Rezeptseite 40 dp** hoch statt 48 dp Touch-Ziel: Mit 48 dp las sich die
   Liste wie eine Einstellungsliste. Die Ziele grenzen lückenlos aneinander und sind seitenbreit;
   im Kochmodus mit Abhak-Kreis 48 dp.
3. **Stepper ohne Ertrag** zeigt „Menge ×1" statt „Portionen": Ohne Zahl sähe man nicht, wie stark
   skaliert ist.
4. **Navigationsleiste 80 statt 64 dp:** Bei 64 dp blieben über der Pill 6 dp (Rückmeldung
   während der Runde). Jetzt 16 dp darüber und 12 dp unter der Beschriftung.
5. **Aktionsleiste 80 statt 72 dp** (im Kochmodus 88 dp): 16 dp Innenabstand ringsum, wie 5.17 ihn
   nennt; mit 72 dp blieben über den Buttons 12 dp, im Kochmodus 8 dp.
6. **Beschriftungen der Navigation wachsen nur bis 130 %:** Vier Ziele teilen sich die Breite; mit
   200 % wurde „Wochenplan" abgeschnitten.
7. **Wochenplan mit 24 dp zwischen Tagen und 12 dp zwischen Gerichten** statt je 8 dp: Mit 8 dp
   waren Tage und Gerichte nicht zu unterscheiden (Rückmeldung während der Runde).
8. **`inkTertiary` minimal angepasst** (#837E76 hell, #837D76 dunkel statt #8F8A81 und #77716A):
   Offener Abhak-Ring und ausgeschalteter Schalter erreichten die 3 : 1 aus Abschnitt 9 nicht.
9. **Screen-Wechsel 220 ms ohne Shared Element** statt 350 ms mit Foto und Titel, die von der Karte
   ins Hero wachsen (4.7): auf dem Gerät unruhig, langsam und ruckelig (Rückmeldung zum ersten
   Draft). Jetzt eine schlichte Überblendung mit wenigen dp Gleiten.
10. **Auswahl-Pills beginnen an der Seitenkante**, bündig mit Suchfeld und Boxen, statt mit ihrer
    Fläche 12 dp über die Kante zu ragen (Rückmeldung zum ersten Draft).
11. **Reduzierte Bewegung:** Android setzt mit „Animationen entfernen" alle Dauern auf null. Aroma
   schaltet dann auf reine Überblendungen ohne Gleiten; kurze sichtbare
   Überblendungen gegen die Systemeinstellung erzwingen wir nicht.
12. **Rasterabstand 28 dp** zwischen Kartenreihen, obwohl 28 nicht in der Abstandsliste steht:
    4.3 verlangt ihn ausdrücklich.
13. **Genau ein Primär-Button** gilt als „höchstens einer": Screens ohne Hauptaktion (Rezepte,
    Wochenplan, Einkauf, Einstellungen) haben keinen, statt einen zu erfinden.
14. **Suche und Profil über dem Gruß** auf schmalen Telefonen (360 dp), wenn „Guten Morgen," nicht
    neben die beiden Buttons passt: sonst bräche der Gruß in drei Zeilen. Ab 411 dp stehen sie wie
    in 6.2 neben der ersten Titelzeile.
15. **Neue Anzeigen aus vorhandenen Daten**, ohne Änderung an API, Datenmodell der Server-Antworten,
    Sync oder Anmeldung: `Recipe.yieldText` liest das schon gelieferte Feld `recipeYield` für die
    Ertragszeile, `RecipeQuery.cookedBefore` stellt die bisherige Abfrage „Lange nicht gekocht" als
    Filter bereit. Die Offline-Kopie bleibt unverändert.

## Offene Punkte

- **Gerät:** Übergänge und Haptik sind in Robolectric und Screenshots geprüft; ob sich 220 ms auf
  dem Telefon richtig anfühlen, zeigt erst der Draft auf dem Gerät.
- **Widgets** nutzen die neue Palette, aber Glance kennt die eigenen Schriften nicht; sie setzen
  die Systemschrift.
- **Silbentrennung:** Wörter mit vier bis sieben Buchstaben trennen nie. Das ist eine Faustregel;
  in sehr schmalen Spalten bei großer Schrift kann ein langes Wort dafür früher umbrechen.
- **Querformat und Tablets:** Telefone hochkant sind auf 360, 411 und 480 dp geprüft, der
  Kochmodus auch quer. Andere Screens im Querformat und Tablets sind lauffähig, aber nicht eigens
  gestaltet (Abschnitt 4.3 nennt Telefone hochkant als Ziel).
- **Live-Tests gegen Mealie** (18) liefen lokal nicht, weil kein Server erreichbar war; in der CI
  des Pull Requests mit dem Mealie-Container sind sie grün.
