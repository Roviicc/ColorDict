# Batch 0001 review sheet — 30-entry calibration pilot

You are the judge, not the writer. For each entry, three questions:

1. **Is it true?** Especially connotation labels and explanations.
2. **Does it read right?** Would a learner understand it? Do examples sound natural?
3. **Is the affix analysis real?** No invented prefixes/suffixes.

## How to look at the entries

Open the real app against the freshly built dictionary:

```
run-desktop.bat --dict data\build
```

Type each word below and read its card. (Or terminal-only:
`run-desktop.bat --dict data\build --lookup cheap`.)

**The 30 words:**
time · hope · failure · run · hold · dismiss · achieve · squander · cheap ·
generous · stubborn · reckless · slender · skinny · unhelpful · reorganization ·
careless · dog · understand · table · light · fine · barely · frankly ·
quickly · not · thrive · elegant · weight · hardly

What I added on top of raw WordNet: connotation **explanations** (grey line
under a definition), extra **examples**, **usage labels** (informal, derogatory
…), **word parts**, and inflections (these power search: try typing "ran" or
"held").

## How to reply

Short notes are enough, in any form, e.g.:

```
F1 agree  F3 agree  F14 neutral is right
cheap ok, stubborn explanation too preachy
dog: soften the wording
```

Anything you don't mention I treat as accepted.

---

## Flags — decisions only you can make

These are places where I believe the **SentiWordNet label is wrong** but the
grounding rule forbids me from changing it. Say "agree" and I'll build the
label-override mechanism and fix them; say "leave" and they stay.

| # | Word / sense | Current label | I think |
| --- | --- | --- | --- |
| F1 | generous — "not petty in character and mind" | **negative** | wrong — clearly positive |
| F2 | generous — "willing to give and share" | neutral | should be positive |
| F3 | skinny — "giving or spending with reluctance" (= stingy) | **positive** | wrong polarity — negative |
| F4 | skinny — "being very thin" | positive | usually mildly negative; slender is the approving word |
| F5 | cheap — "embarrassingly stingy" | neutral | should be negative |
| F6 | hope — "someone on which expectations are centered" | **negative** | wrong — "the team's great hope" is positive |
| F7 | light — "of comparatively little physical weight" | negative | plain neutral |
| F8 | weight — "sports equipment" (dumbbells) | negative | plain neutral |
| F9 | time — "an indefinite period" | positive | plain neutral |
| F10 | hold — "be capable of holding or containing" | positive | plain neutral |
| F11 | achieve / thrive / squander — all | neutral | achieve & thrive positive, squander negative; SentiWordNet simply missed them |
| F12 | unhelpful | neutral (score −0.125, below our ±0.25 threshold) | mildly negative — is the threshold too strict? |
| F13 | **not** | **negative** | function word — negation is not a connotation. Policy: function words get no label at all? |

## Judgment calls in my own work — push back freely

| # | What I did | The question |
| --- | --- | --- |
| J1 | cheap (price sense): wrote an explanation *supporting* the negative label ("often implies low worth; 'inexpensive' is neutral") | Is that the right call, or is the price sense neutral? |
| J2 | hardly = hard + -ly | Analysis is historically true but the meaning shifted ("almost not" ≠ "in a hard manner"). Keep the analysis, or is it misleading? |
| J3 | generous = gener + -ous; dismiss = dis- + miss | Bare Latin roots ("gener", "miss") — helpful or confusing? Alternative: mark them analysable:false |
| J4 | understand: **no** analysis (refused under- + stand) | Confirm — this was the deliberate trap |
| J5 | dog (insult senses): explanations warn "avoid" / "demeaning" | Right tone for sensitive usage, or should the dictionary describe without advising? |
| J6 | Inherited WordNet examples often illustrate a *synonym*, not the headword ("a flashy ring" under cheap) — 180 validator warnings | Keep them (they illustrate the sense) or drop them at review tier? |

## After your reply

I fold in every correction, adjust the enrichment prompts/rules from the
pattern of what you rejected, flip these 30 entries to `reviewed`, and rebuild.
Your verdict rate here calibrates whether B2 (1,000 words) is ready to run.
