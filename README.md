# Aroma

Eine Android-App für [Mealie](https://mealie.io), den selbst gehosteten Rezeptmanager. Aroma
bringt deine Rezepte, den Wochenplan und die Einkaufslisten auf das Handy, wie ein gedrucktes
Kochbuch: ruhig, gut lesbar und auch in der Küche mit einer Hand zu bedienen.

## Was Aroma kann

**Rezepte**
- Suchen, sortieren und filtern nach Favoriten, Kategorien, Schlagwörtern und Utensilien
- Kochbücher und Mealies Zeitstrahl im Rezepte-Tab
- Portionen umrechnen, mit echten Brüchen; Zutaten abhaken
- Langes Drücken auf ein Rezept: kochen, einplanen, einkaufen, merken, teilen

**Kochen**
- Kochmodus mit großer Schrift; auf Wunsch stehen die Zutaten unter jedem Schritt und lassen
  sich dort abhaken
- Timer, die Aroma im Schritttext erkennt und die auch bei geschlossener App klingeln
- „Gekocht“ trägt das Rezept in Mealies Zeitstrahl ein, danach fragt die Startseite, wie es
  geschmeckt hat

**Planen und Einkaufen**
- Wochenplan mit Vorschlägen auf der Startseite, passend zur Tageszeit
- Geplante Gerichte verschieben, die ganze Woche mit einem Tipp auf die Einkaufsliste setzen
- Einkaufslisten nach Labels gruppiert, mit Einkaufsmodus; Änderungen ohne Netz werden
  nachgereicht

**Importieren**
- Rezepte per Link, über „Teilen“ aus jeder App oder aus eingefügtem Text
- Beiträge von Instagram, TikTok, Facebook und Threads liest Aroma selbst aus und legt sie in
  Mealie als ein fertiges Rezept an, mit Link und Foto
- Mit Mealies KI, auf Wunsch übersetzt (Mealie 3.23 oder neuer mit eingerichtetem KI-Anbieter)
- Jeder Schritt ist sichtbar; verlässt du die App, läuft der Import weiter und eine
  Mitteilung sagt dir, wenn das Rezept da ist

**Außerdem**
- Alle Rezepte offline auf dem Gerät, auf Wunsch
- Verknüpfungen auf dem App-Symbol: suchen, neues Rezept, Wochenplan, Einkaufsliste
- Updates direkt aus den Einstellungen
- Hell, dunkel oder Material You; Deutsch und Englisch

## Installieren

Die APK liegt bei jedem [Release](https://github.com/hazymorning/Aroma/releases). Nach der
Installation sucht Aroma auf Wunsch beim Start nach neuen Versionen und installiert sie aus den
Einstellungen. Jede Version ist mit demselben Schlüssel signiert, Android prüft das bei jedem
Update.

Voraussetzungen: Android 8.0 und Mealie 2.0 oder neuer. Anmelden kannst du dich mit Passwort
oder mit einem API-Token aus Mealie.

## Datenschutz

Aroma spricht mit deinem Mealie-Server und sonst mit niemandem, mit zwei Ausnahmen, die du
auslöst: Beim Import eines Social-Media-Beitrags liest Aroma dessen Vorschau bei der Plattform,
so wie ein Messenger beim Teilen eines Links. Und die Suche nach Updates fragt GitHub nach der
neuesten Version. Aroma sammelt keine Daten und enthält keine Werbung und keine Tracker.

## Entwickeln

```
./gradlew assembleDebug
```

`scripts/check.sh` führt dieselben Prüfungen aus wie die CI: Formatierung, Lint, alle Tests und
die Screenshots. Mit `MEALIE_TEST_URL`, `MEALIE_TEST_USER` und `MEALIE_TEST_PASSWORD` laufen
zusätzlich die Tests gegen einen echten Mealie-Server.

Das Erscheinungsbild folgt festen Regeln, beschrieben in [docs/DESIGN.md](docs/DESIGN.md); ein
Test prüft sie bei jedem Build.

---

**In English:** Aroma is an Android app for the self-hosted recipe manager Mealie: recipes,
cookbooks and the timeline, a cooking mode with timers and the ingredients under every step, a
weekly plan with suggestions, shopping lists that work offline, and imports from links, shared
text and social media posts that end as one finished recipe, with progress you can follow and a
notification when it is done. It needs Android 8.0 and Mealie 2.0 or newer; get the APK from the
releases page and update from the app's settings.
