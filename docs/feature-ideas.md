# ColorDict: feature research from Mobbin

Research only, no code. I ran 12 Mobbin searches (11 for screens, 1 for flows; iOS, plus one web search) across dictionary, vocabulary, flashcard, quiz, reading, widget, stats and reminder apps. I looked at the screenshots themselves. The apps that matched best were **Vocabulary** (a word-of-the-day app with a warm paper palette and serif headwords, very close to ColorDict's look), Quizlet, Preply, Babbel, Speak, CapWords, Headway and Fable. The web search for dictionary or thesaurus pages mostly returned glossary and admin tools, so it added little.

Effort assumes plain Java with framework views only and no libraries: S = about a day, M = a few days, L = a week or more.

---

## 1. Ranked shortlist

### 1. Word of the Day (offline)
- **What:** Each day the home screen shows one featured word: headword, IPA with the speaker pill, a one-line gloss and an example, plus bookmark and share. The word is picked from WordNet with a seed based on the date. Limit the pool to "interesting" words, for example adjectives and verbs that have examples and fall in a mid-frequency band, so it doesn't land on "the" or on obscure taxonomy terms.
- **Why it fits:** It gives people a reason to open the app when they don't need to look anything up. It reuses the existing entry renderer, needs no network, and the Vocabulary app shows the paper-and-serif style working well as a hero card.
- **Effort:** S. **Network/Claude:** No. You could optionally show the saved connotation card if the user has checked that word before.
- **Refs:** [Vocabulary: "indelible" hero card](https://mobbin.com/screens/68350abc-944f-4c13-aa39-b2016587ac8e) · [Vocabulary: Favorites flow, "seraphic" with example and action row](https://mobbin.com/flows/84127d76-b779-4f86-9f5b-2ea99f019a0e) · [The Outsiders: word card with US/UK IPA and plural](https://mobbin.com/screens/37b767e5-9e3d-40f0-83c1-9526f913d96e)

### 2. Home-screen widget (Word of the Day, or a random saved word)
- **What:** A resizable `AppWidgetProvider` built with `RemoteViews` that shows the headword, part of speech, gloss and example. Tapping it opens the entry. A setting chooses the source: Word of the Day or a random bookmarked word, which works as passive review.
- **Why it fits:** It sits naturally on top of #1. Vocabulary shows a preview of the widget inside its own settings, styled in the app's theme.
- **Effort:** M. RemoteViews limits styling, and you need to schedule daily updates. **Network/Claude:** No.
- **Refs:** [Vocabulary: widget setup with live preview](https://mobbin.com/screens/01b7b667-ea50-47ef-9e6f-e563030e0b36) · [Deepstash: quote and streak widgets](https://mobbin.com/screens/a9dee944-e751-4b82-ab90-470dc9fb24d0) · [Ahead: "Add widget" prompt](https://mobbin.com/screens/dfef7265-8056-42c6-8d39-4e103235f44d)

### 3. Flashcard review of saved words with spaced repetition
- **What:** A "Review" button on the Saved tab. Cards show the headword with a speaker icon; tapping flips to the meaning and example. Swipe right for "Know it", left for "Still learning", with a counter (3/20) and undo. Scheduling uses a simple Leitner box or SM-2, stored as a due date and box number per bookmark in SQLite.
- **Why it fits:** It turns the existing bookmarks into practice, and it is the core loop in every vocabulary app I found. The popup already feeds the Saved list from e-book readers, so reading leads straight into review.
- **Effort:** M. Card flip animation and swipe handling are doable with `ViewPropertyAnimator` and the framework's `GestureDetector`. **Network/Claude:** No.
- **Refs:** [Quizlet: card with counter, swipe hint, undo](https://mobbin.com/screens/80a4c6a5-566b-4005-a227-16ff5eac01e0) · [Preply: card with example, progress bar, delete plus Undo](https://mobbin.com/screens/fa013bb4-d953-42e0-8191-15d0c973ecdc) · [Babbel: lesson review flow ending with a results summary](https://mobbin.com/flows/78c61a08-f401-4bf0-a9fc-21caf3b675d4)

### 4. "Meaning match" quiz
- **What:** A quiz that shows a headword with three or four glosses, or a gloss with three or four words, and asks which one matches. Wrong answers come from WordNet glosses of words with the same part of speech; nearby or similar words make it harder. Saved words come first. It ends with a summary screen listing the words you got right and wrong.
- **Why it fits:** It works fully offline from data the app already ships, and gives a second way to practise next to flashcards.
- **Effort:** M. **Network/Claude:** No.
- **Refs:** [Vocabulary: "Meaning match"](https://mobbin.com/screens/e2d3521d-ede4-4a33-a712-a44c07211aaf) · [Quizlet: gloss leading to choose-the-word](https://mobbin.com/screens/f8af7017-ac7b-4584-bd0c-9845d160911a) · [Preply: choose the word, with an "end practice?" sheet](https://mobbin.com/screens/4229fa4c-fb3f-4aee-a365-460650a65f20)

### 5. Word lists (folders for Saved)
- **What:** Users can create named lists ("Novel – Middlemarch", "GRE", "Work writing"). A word can belong to several lists. The Saved tab gets a Lists view with a "+ New list" row first, then each list with its count. Bookmarking from the popup could long-press to choose a list. Review (#3) and quiz (#4) can run on one list.
- **Why it fits:** Reading-driven lookups pile up quickly, and lists keep them in context.
- **Effort:** M. **Network/Claude:** No.
- **Refs:** [AllTrails: Saved with "Create new list" and counts](https://mobbin.com/screens/6cc2580b-86aa-4b71-b0f4-dfdbdda60af9) · [Booking.com: simple named lists with counts](https://mobbin.com/screens/a28ed481-2da2-45b5-90e8-f5a57039f2fd) · [Vocabulary: Favorites / My own words / Collections / Make your mix](https://mobbin.com/flows/84127d76-b779-4f86-9f5b-2ea99f019a0e)

### 6. Save the context sentence with a bookmark
- **What:** When a word is saved from the popup, also store where it came from: the full selected text, if the user selected more than one word, the source app's label, and the time. The full entry and the flashcard then show "Seen in: *…sentence with the word highlighted…*" and "from Kindle, 3 days ago". The user can also paste or edit the sentence.
- **Why it fits:** It builds on what makes ColorDict different, the popup used from any app. Headway's cards show exactly this: the word highlighted inside the sentence where it was found. One limit: `PROCESS_TEXT` passes only the selected text, so the sentence is only captured when the user selects it.
- **Effort:** S–M. **Network/Claude:** No.
- **Refs:** [Headway: word card with source sentence and highlight](https://mobbin.com/screens/571fcfe9-a508-44d3-b672-3f9babf8feab) · [Fable: reader selection menu with "Dictionary"](https://mobbin.com/screens/37c599ad-b4f0-469e-825e-6e2e972b6953)

### 7. "Which meaning?" picker when saving
- **What:** When the user bookmarks a word with several senses, a small sheet lists the senses as cards (POS label plus gloss) so they can mark the one they meant. Flashcards and quizzes then test that sense instead of sense #1.
- **Why it fits:** WordNet words often have five or more senses, and testing the wrong one makes review frustrating. It is also cheap to build.
- **Effort:** S. **Network/Claude:** No.
- **Refs:** [Preply: "Choose a meaning" sense cards](https://mobbin.com/screens/0b9cdcd2-a4ab-4e8e-8dcc-5aa90d724c37)

### 8. Claude "nuance compare" for synonyms (extends connotation)
- **What:** An optional button next to the synonym chips, "How do these differ?". It sends the headword and its WordNet synonyms to Claude and gets back a short card with one line per synonym (register, intensity, typical context) and a suggestion of which to use when. Cache it per word, the same way connotation results are cached.
- **Why it fits:** It is the natural next step after connotation. WordNet lists synonyms but never says how they differ, and that is exactly the question a writer has. It also reuses the existing API key, model setting and cache. Speak shows "Similar words" as cards with a gloss under each, and Confluence's AI definition card shows how to label AI-generated content with a "Not quite right?" control.
- **Effort:** M, mostly prompt and parsing. The UI matches the connotation card. **Network/Claude:** Yes.
- **Refs:** [Speak: "About the word" plus "Similar words" cards](https://mobbin.com/screens/6cda687c-0218-4908-9d4c-86fa22cfc242) · [Confluence: AI definition with BETA, sources and "Not quite right?"](https://mobbin.com/screens/7d6e5939-7b8b-4932-8fae-26ad6b298e90)

### 9. Stats and streak
- **What:** A small "Your words" screen: lookups this week, words saved, words reviewed, words mastered (Leitner box 5+), and a week row of dots for days with a review. This is local data only, with no leaderboard.
- **Why it fits:** It is cheap once #3 exists, and it gives a light reason to keep reviewing. Vocabulary's stats page uses the same paper style.
- **Effort:** S–M. **Network/Claude:** No.
- **Refs:** [Vocabulary: Word stats (Read / Favorited / Saved / Practices, streak dots)](https://mobbin.com/screens/ab8cfc23-fe79-4778-84ee-ddc2895aab55) · [CapWords: day streak, "Got it / Needs review", calendar](https://mobbin.com/screens/e5673646-4d12-4608-9c2a-af50f16b39f6) · [Headway: streak with weekday circles](https://mobbin.com/screens/0a161ddb-aaac-483a-876b-ead2c82a6023)

### 10. Daily reminder notification
- **What:** An opt-in setting, "Daily word / review reminder" at a time the user picks. It posts a notification with the Word of the Day, or "7 words due for review", using `AlarmManager` and a `NotificationChannel`. Android 13+ needs the `POST_NOTIFICATIONS` permission prompt.
- **Why it fits:** It connects #1 and #3 to a daily habit. The settings UI is one toggle plus a time row.
- **Effort:** S. **Network/Claude:** No.
- **Refs:** [Coursera: "Learning Reminders… at times you set"](https://mobbin.com/screens/31c15420-07f5-4621-8b3b-ede2bb32d285) · [Headway: opt-in toggles with plain-language descriptions](https://mobbin.com/screens/6c5c2dad-0539-49dd-8d3b-3abaf34b63f7)

### 11. Browse by topic (WordNet lexicographer categories)
- **What:** A "Browse" entry with topic tiles (Body, Food, Feelings, Animals, Communication, Weather…) built from WordNet's lexnames (`noun.body`, `noun.feeling`, `verb.communication`…). Each tile opens a scrollable word list, and the user can start a quiz on a topic.
- **Why it fits:** It browses the data the app already has, offline, with no curation. Vocabulary's "By topic" grid shows the pattern.
- **Effort:** M. It may require exporting lexname into the bundled DB if it isn't already there. **Network/Claude:** No.
- **Refs:** [Vocabulary: Categories, "By topic" grid (in Favorites flow)](https://mobbin.com/flows/84127d76-b779-4f86-9f5b-2ea99f019a0e)

### 12. Export / import saved words
- **What:** Export the saved words (word, chosen sense, list, context sentence, connotation) as CSV or JSON through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`), and import them back. This also covers moving to a new phone and loading the words into Anki.
- **Why it fits:** "Clear data" exists today but there is no backup, and the app has no account to sync with.
- **Effort:** S. **Network/Claude:** No.
- **Refs:** [Grammarly: dictionary list with "Import list" and "Export"](https://mobbin.com/screens/a224ff81-2a08-4bf6-bf92-3b4f3da00825)

---

## 2. Design patterns worth borrowing

- **Bold the headword inside example sentences.** Vocabulary bolds "veracious" in each example, which makes examples much faster to scan. ([ref](https://mobbin.com/screens/ef8e9a40-0a90-4843-af41-d5d6b9207f1d))
- **A large, spaced bottom action row** (share / favorite / bookmark) on the entry instead of small toolbar icons. ([ref](https://mobbin.com/screens/ef8e9a40-0a90-4843-af41-d5d6b9207f1d))
- **Synonyms as cards with a mini-gloss** for the top 3, keeping chips for the rest. You can tell which synonym fits without tapping. ([Speak](https://mobbin.com/screens/6cda687c-0218-4908-9d4c-86fa22cfc242))
- **Snackbar with Undo** after removing a bookmark or recent item, instead of a confirmation dialog. ([Preply](https://mobbin.com/screens/fa013bb4-d953-42e0-8191-15d0c973ecdc))
- **Swipe-direction hint text** under a card ("Swipe left to mark *Still learning*") plus a counter at the top. ([Quizlet](https://mobbin.com/screens/80a4c6a5-566b-4005-a227-16ff5eac01e0))
- **An end-of-session summary** with "Correct" and "Incorrect" groups of word chips, each with a speaker icon. ([Babbel flow](https://mobbin.com/flows/78c61a08-f401-4bf0-a9fc-21caf3b675d4))
- **A "Not quite right?" feedback line on AI cards**, plus a small BETA label and "content quality may vary". Apply it to the connotation card. ([Confluence](https://mobbin.com/screens/7d6e5939-7b8b-4932-8fae-26ad6b298e90), and CapWords' "Not what you expected? Tap to adjust" ([ref](https://mobbin.com/screens/348b023e-552b-4347-ac6a-2cb3c4442625)))
- **A live widget preview inside Settings**, themed like the app. ([Vocabulary](https://mobbin.com/screens/01b7b667-ea50-47ef-9e6f-e563030e0b36))
- **A streak shown as a row of weekday dots** instead of a big number. It is calmer and fits the paper aesthetic. ([Vocabulary stats](https://mobbin.com/screens/ab8cfc23-fe79-4778-84ee-ddc2895aab55))
- **A friendly empty state that says how to fill the list** ("No words saved — select text in any app and tap Define"). ([Speak saved words flow](https://mobbin.com/flows/6f9e7255-5d6e-4bfa-9918-7bd69fc3f041))
- **Opt-in toggles each with a one-line plain-language description**, for any new notification or widget settings. ([Headway](https://mobbin.com/screens/6c5c2dad-0539-49dd-8d3b-3abaf34b63f7))

---

## 3. Considered and rejected

- **Paywall or locked sections** (Vocabulary's "Unlock everything" over blurred content): the app has no business model that needs it, and blurred content hurts a dictionary.
- **Leaderboards or "you outrank 4% of learners"**: needs a server and accounts.
- **Speaking or pronunciation practice with the mic** (Duolingo): speech recognition needs Google services or network, and that is off-mission for a dictionary.
- **Camera capture of objects into words** (CapWords): needs ML or vision. Too heavy, and it doesn't fit English definitions.
- **Multilingual translation** (Speak, Preply): no bilingual data, so it is out of scope.
- **AI tutor chat** (Speak Tutor): high effort and cost for the user's key, and it overlaps with simply asking Claude elsewhere.
- **Etymology / "Origin" section**: WordNet has no etymology. It could come from Claude, but that risks hallucinated etymologies shown as dictionary fact.
- **Illustrated or animated word cards** (The Outsiders, CapWords): needs image assets for each word.
- **Personalized "feed from your interests" onboarding** (Vocabulary): a lot of machinery for little gain in a lookup-first app.
- **Team or shared glossary** (Grammarly, WRITER): enterprise feature, not relevant.

---

No search result included an `ai_usage_notice`.
