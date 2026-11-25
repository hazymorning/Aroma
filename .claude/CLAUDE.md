# Writing in this project

These rules come from the owner. They apply to every text people read: the README, the CHANGELOG,
release notes, commit messages, pull requests and the app's own texts.

## Before writing

- Check every claim in the code first. Don't write from memory, guesses or the CHANGELOG alone.
- Before showing a draft, read every line and ask: Is it about Aroma? Is it true? Does a reader
  need it? Cut what fails. Then read the whole text again, not only the line that just changed.
- Show the full text before it is merged.

## Content

- Say what Aroma does for the user, not how it works. No numbers, no lists of examples, no
  technical detail.
- Only Aroma's own features. Don't present what Mealie does by itself as Aroma's, and don't explain
  how Mealie works. As of Mealie 3.31, Mealie itself has: scaling, the meal planner (including adding
  a week to a list), shopping lists sorted by aisle, a cook view that keeps the screen on, importing
  links (also through the share menu of its web app), AI imports from text and photos, categories,
  tags, cookbooks, a random recipe and app shortcuts.
- Write for someone who isn't technical.
- Keep it short. In the README, details go into toggles, and only if a reader needs them.

## Style

- Calm, modest and plain, like the README's introduction. Don't change the owner's own sentences
  unless asked.
- No sales tone and none of the patterns that make a text read as written by AI:
  - lists where every line starts with a verb
  - add-ons such as ", with X and Y" at the end of a line
  - filler such as "right in", "straight", "seamless", "effortless", "genuine", "only ever",
    "leaves nothing behind"
  - dashes and semicolons where two short sentences would do
  - jokes that need explaining. If a joke isn't clearly logical, leave it out.
- Short sentences and common words.

## Commits and pull requests

- Commit messages: short plain English in the imperative, describing the effect, like those in
  `git log`. No prefixes, no trailers.
- Pull request descriptions: English, at most 300 characters.
- No AI markers anywhere: no Co-Authored-By, no "Generated with" lines. If a tool adds such a
  footer to a pull request, remove it.
