# Review: learner-reader.md (draft)

**Verdict: place with the listed changes.**

The design is sound. It judges ease only, it is blind to the author and the charge, it is binary, and it needs evidence before it marks a note hard. Five problems would make its verdicts unreliable against the 9-in-10 gate:

- its hard-word rule disagrees with simplifier.md and plain_lint;
- its grammar-term list is a different list;
- it tells the reader, in two places, that the notes are rewrites;
- nothing in it protects against the reader's own fluency, which biases it toward easy;
- the output does not say what to quote for a sentence-level stop.

Changes 1 to 3 are required. Changes 4 to 8 are strongly recommended. Change 9 is a condition on how the file is placed, not an edit to its text.

---

## 1. Align the hard-word rule with simplifier.md and plain_lint (required)

simplifier.md says: "One hard word may stay when no everyday phrase does its work; two may not." plain_lint flags `hard-words` only at two or more (`RARE_LIMIT = 2`). It exempts the headword, any form of the headword (its 5-letter stem check, so *froth* in the note on *frothy*) and every `*neighbour*`.

The draft says two different things:
- line 45 marks a note hard for "a word they would not know, and no everyday word around it that explains it". That is **one** unexplained word.
- line 51 says "Two unexplained ones are" a stop.

These two lines contradict each other. A reader following line 45 would mark hard the simplifier's own model note: *asinine*, "Withering - stupidity so complete it deserves contempt." *withering* has a Zipf score of 2.82, and plain_lint passes the note. So the writer would be failed for obeying its own rule, and the gate would be biased toward hard.

Replace lines 36-37 with:

```
- the words are everyday words, or the headword or a form of it, or a
  neighbour named in `*asterisks*` (the learner can tap those)
```

Replace lines 45 and 51 with:

```
- two or more words a learner would not know, not counting the headword, a
  form of it, or a `*neighbour*`
```

```
One word a learner would not know is allowed, whether or not the sentence
explains it: sometimes it is the exact word the note needs. Two are a stop.
If you mark a note hard for words, `stops` must quote at least two.
```

## 2. Use the same grammar-term list as the writer and the lint (required)

The draft's list includes *connotation*, which neither simplifier.md nor plain_lint's `GRAMMAR` contains. It leaves out *negation* and *gloss*, which simplifier.md bans by name. It also says nothing about the school words plain_lint deliberately leaves alone.

Replace lines 40-41 with:

```
- nothing in it is a grammar or linguistics term the learner would have to
  look up: *participle*, *superlative*, *comparative*, *negation*,
  *register*, *euphemism*, *pejorative*, *evaluative*, *gloss*, *prefix*,
  *suffix*, *connotation*, and words like them. School words a learner is
  taught early - *noun*, *verb*, *adjective*, *spelling*, *metaphor*,
  *literal* - are everyday words here. So are labels like *formal*,
  *informal*, *British*, *slang*.
```

If *connotation* stays in the reader's list, add it to `GRAMMAR` in `tools/plain_lint.py` and to the list in simplifier.md, so all three hands use one list.

## 3. Stop telling the reader these are rewrites (required)

Line 25 ("not what it replaced") and line 67 ("what they replaced") tell the reader that each note replaced another one. A reader that knows it is checking a simplification pass will expect plain notes and read toward easy. That is the direction the pass is hoping for, so it is the direction of the bias.

Replace lines 24-25 with:

```
the learner sees above the note) and the `note`. Nothing else: not who wrote
it, not when, not whether anyone thinks it is true.
```

Replace lines 66-69 with:

```
Your tools read your own packet and write your own verdict file. You are not
shown who wrote the notes or how they were scored, and you should not try to
find out - not the shards, not the censuses, not the other packets. The packet
in front of you is the whole of the evidence.
```

Add this paragraph after line 25:

```
Your packet may hold notes from anywhere in the dictionary, long and short,
old and new. Do not assume any of them was written to be easy.
```

This sentence is also what makes change 9 work.

## 4. Anchor the learner and correct for your own fluency (recommended)

"Good enough to read a novel slowly, with help" is loose. The largest risk to this instrument is that a fluent model does not notice a word that is hard for a learner, and so passes it. That bias points toward easy, which is the direction the gate rewards. Give the reader a concrete learner and one easy and one hard example.

Replace lines 13-15 with:

```
tapped a word to find out how it feels. Your English is upper-intermediate
(about B2): you read a novel slowly, with a dictionary. You know the words
you would meet every week in news and conversation. You do not know words
you would meet only a few times a year - rare, literary or technical words -
and grammar terms mean little to you. You yourself know far more English
than this learner; when you are unsure whether a word would stop them, ask
whether it is a word you would meet only in books.
```

Add at the end of "How to decide":

```
Two notes to calibrate against:

- *clapped out* - "British, and it works equally on a car, a machine or a
  person - which is the joke." Easy: every word is everyday.
- "Convicts the thing of mileage rather than damage." Hard: *convicts* is
  used as a figure the learner cannot follow, and the sentence has no
  subject to hold on to.
```

(The hard example is the one plain_lint's header uses for a note that is true but hard to read. Neither example comes from a packet this reader will see.)

## 5. Make the figure-of-speech rule fit vivid notes (recommended)

Line 49 would catch most vivid notes. Simplifier.md asks for "short, vivid" notes, and census-reader.md says "Do NOT mark a note wrong for having personality." So the reader would mark hard the notes the other two instruments ask for.

Replace line 49 with:

```
- an idiom or figure of speech whose plain reading would mislead the learner
  or leave them with nothing ("convicts the thing of mileage"). A vivid word
  used in its everyday sense is not a figure: "stupidity so complete" is
  plain.
```

## 6. Keep truth out of `why` as well as the verdict (recommended)

Lines 29-32 keep truth out of the verdict, but `why` is free text, and a reader will fill it with "and this is not what the gloss means". That is where it overlaps the census reader. Also, OEWN glosses are often harder than the notes, and the draft does not say the gloss is excluded from judgment.

Add after line 32:

```
Judge only the words of the note. The gloss is there so you know what the
note is about; a hard word in the gloss is not a stop in the note. Never
write in `why` that a note is wrong, inaccurate, or does not fit the gloss -
not even in passing.
```

## 7. Make `stops` testable for sentence-level stops (recommended)

"`stops` quotes the note exactly" works for a word. For a sentence that must be read twice, it does not say what to quote. Readers will quote the whole note, or nothing. The draft also has no way to tell word stops from sentence stops, and the aggregate needs that to decide whether a failed pilot needs a simpler vocabulary or a shorter sentence.

Replace lines 88-95 with:

```
 "<id>": {"verdict": "hard",
          "stops": [{"kind": "word|grammar|sentence|figure",
                     "quote": "<copied from the note>"}],
          "why": "<one sentence: what stops the learner there>"}
}
```

```
Each `quote` is copied from the note character for character, including
dashes, asterisks and punctuation, so it can be found in the note by search.
For `word`, quote each hard word. For `sentence`, quote the shortest span
where the learner loses the thread (for example, from the subject to its
far-off verb), not the whole note. A `hard` verdict has at least one stop.
An `easy` verdict carries nothing else.
```

## 8. Put the packet number in the output (recommended)

Census-reader writes `{"packet": N, "verdicts": [...]}` in input order. This draft writes a bare object keyed by id "in packet order". JSON objects have no reliable key order, and nothing ties the file to its packet if it is moved.

Replace lines 82-83 with:

```
Write JSON to the output path you were given: `{"packet": <packet number>,
"verdicts": {...}}`, with one key per note id inside `verdicts`, in packet
order.
```

If you would rather match census-reader exactly, use a list of `{"id", "verdict", "stops", "why"}` objects instead.

## 9. Condition on placement: seed controls (not a text change)

No wording in the rubric can prove that a reader which marks 10 of 10 easy is right and not simply lenient. So the learner packets, which no tool builds yet (`plain_packets.py` has no learner step), should mix in two sets of controls without labels:

- about two original notes that plain_lint flags as `hard-words` or `grammar`, which should read hard;
- about two short notes that pass the lint and were never flagged, which should read easy.

Score the controls separately from the 9-in-10 rate, and count a pilot only if the reader gets them right. Change 3 is what keeps these controls blind.

Also, as the overnight plan requires: add `learner-reader.md` to `INSTRUMENTS` in `tools/instrument_gate.py`, and record the new baseline in the plan.

---

## Checked and sound

- `model: fable` against the simplifier's `opus` keeps the hand that writes away from the hand that reads.
- `tools: Read, Write` matches the other two instruments.
- The reader is not given the charge, the neighbours' notes, or the flag and its reason. None of these are needed, and all of them would leak.
- The reader is not told the 9-in-10 threshold. Keep it that way.
- The "Use this file verbatim" section matches house style.
- "If you cannot point at a word or a place, it is easy" is the right tie-break once changes 4 and 9 guard against the easy bias.
- The reader has no length rule. That is correct: length is plain_lint's gate, and the reader should not count words.
