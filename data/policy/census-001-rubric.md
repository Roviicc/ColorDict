# Census 001 — reading rubric

You are reading tone notes from a connotation dictionary against the OEWN
gloss they are printed under. Four sampled audits (plan 11.65-11.68) named the
fault classes; your job is to apply them to every sense in your batch, read
carefully, one at a time. Quality of reading is the entire point: fifty senses
read properly beat five hundred skimmed.

## The two binding rules

1. **The gloss is binding.** The note must agree with the definition printed
   above it - not with the lemma in general, not with the word's commonest
   sense. `vociferous` glossed "conspicuously and offensively loud" cannot be
   noted "which is why it can be praise". `voracious` glossed on food cannot
   be noted on reading.

2. **Stay inside the word.** Say what the word does - its force, its register,
   how it differs from its neighbours. Do not say who uses it, how often, or
   where it came from. We have no corpus and no etymological source, so those
   claims are guesses wearing the clothes of facts. Three shapes are out:
   - distribution: "usually", "now mostly", "the commonest"
   - provenance: any origin story ("From minor-league baseball", "Church Latin")
   - restriction: narrowing the sense past what the gloss says it covers

## What passing looks like (kept from audit 001)

- snivel: "Contemptuous - crying treated as whining, weakness rather than sorrow."
- asinine: "Withering - stupidity so complete it deserves contempt."
- clapped out: "British, and it works equally on a car, a machine or a person - which is the joke."

Vivid is good. Register labels (British, formal, informal) are fine - they are
usage facts of the kind dictionaries record. Do NOT mark a note wrong for
having personality. Mark it wrong when it leaves the gloss or leaves the word.

## What failing looks like

- kindly noted "now used mostly of the elderly" (distribution)
- laud noted "Church Latin behind it" (provenance)
- blatant glossed "conspicuously and offensively loud" noted "has largely left sound behind" (contradicts the gloss)
- vulgarly glossed "in a smutty manner" carrying the note for vulgar "lacking refinement" (wrong sense)

## Verdicts

- `ok` - the note describes this gloss's sense, stays inside the word, and the
  charge sign is plausible for the gloss.
- `no` - it fails one of the rules. Name the fault:
  - `wrong-sense` - the note describes a different sense from the gloss above it
  - `unverifiable` - a claim about speakers, frequency or origin we cannot check
  - `note-scope` - the note leaves the word (references the sample, or restricts
    beyond the gloss)
  - `wrong-gloss` - OEWN put the word in a synset that does not fit it (flag it;
    we cannot fix a gloss, the sense will decline judgement)
  - `charge-sign` - the numeric charge contradicts the gloss (e.g. clearly
    negative gloss carrying a positive charge)
  - `other` - a fault outside these classes; describe it precisely, this is how
    a fifth fault class gets discovered
- `hm` - genuinely unsure after careful reading. Use it honestly; do not use it
  to avoid deciding.

## Also check

- The charge (integer, negative = disparaging, positive = approving, 0 =
  neutral-in-family) against the gloss.
- Examples, if present: they must fit THIS sense of the word.

## Output

Write JSON to the output path you were given, exactly this shape, one object
per sense, in input order:

{
 "batch": "<input file stem>",
 "reads": [
  {"id": "<sense id>", "word": "<word>", "verdict": "ok|no|hm",
   "fault": "<class, only when verdict is no>",
   "why": "<one sentence, only for no/hm>",
   "repair": "<suggested replacement tone note obeying both rules, or the word 'drop' to remove the note, or 'skip-gloss' when the gloss itself is the problem; only for no>"}
 ]
}

Do not edit any other file. Do not fix anything yourself - repairs are applied
centrally so they land synset-wide. Your output file is your entire deliverable.
