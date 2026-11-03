# PopUp Dictionary: Play Store launch plan

Goal: publish PopUp Dictionary on Google Play as a free app, with an optional
"Buy me a coffee" tip, as an alternative to ColorDict.

> Google changes Play rules often. Items marked **(check)** are what I believe
> is current; confirm them in Play Console when you get there.

## 1. Decisions to make first (cannot be changed after the first upload)

| Decision | Why it matters | Recommendation |
|---|---|---|
| **Package name** (application ID) | Permanent on Play; it is the app's identity. Today it is `io.github.roviicc.colordict`, which has "colordict" in it. | Change before the first upload, e.g. `com.<yourname>.popupdictionary`. Phones with the current test builds will need one reinstall. |
| **Developer account type** | Personal accounts must run a closed test first **(check)**; organization accounts need a D-U-N-S number. | Personal is fine to start. |
| **App name** | "PopUp Dictionary" must not be confusable with ColorDict and should not already be taken. | Search Play for "PopUp Dictionary" in your country before committing. Never use "ColorDict" in the name, icon or screenshots; the description may say it works with e-book readers that support ColorDict-style lookups. |

## 2. Work I will do (code and assets)

- [ ] **Package name change** once you pick one.
- [ ] **Target Android 16 (API 36)**: Play requires new apps to target a recent API level **(check: likely 36 now)**. This also turns on predictive back, so the popup's "back shrinks the sheet" logic must move to the new back-callback API.
- [ ] **Release signing + App Bundle**: Play takes a signed `.aab`. The CI release workflow will build one, signed with your upload key from GitHub secrets. The current debug-key signing stays for test APKs only.
- [ ] **Test the shrunk (R8) release build on a real phone**: the Claude SDK uses reflection, which shrinking can break. Not tested on a device yet.
- [ ] **Privacy policy page** (required because the app uses the network): hosted free on GitHub Pages. Content: no account, no analytics, saved words stay on the phone, and the word (plus its dictionary meanings) is sent to Anthropic only when the user taps a Claude button, using their own API key.
- [ ] **Data safety form answers** to match the policy (I will draft them).
- [ ] **Store listing**: short description (80 characters max), full description (4,000 max), 4–6 phone screenshots, a 1024×500 feature graphic, and the 512×512 icon (done: `docs/store/icon-512.png`).
- [ ] **First-run screen** explaining that the dictionary works offline and the API key is optional (only for Claude features).
- [ ] **Tip jar** (see section 4).

## 3. Steps only you can do

1. **Create a Google Play Console developer account**: one-time US$25 fee plus identity verification. It can take a few days.
2. **Create an upload key** on your computer (I will give you one command) and add it as four GitHub repository secrets. Keep a backup: losing it means asking Google to reset it.
3. **Set up a payments profile** in Play Console (bank and tax details). This is needed to receive tips.
4. **Recruit testers for the closed test**: personal accounts need at least 12 testers opted in for 14 days in a row before production access **(check)**. Their Gmail addresses or a Google Group work.
5. **Fill in the questionnaires**: content rating, target audience (not children), ads (none), data safety (I will draft the answers).

## 4. "Buy me a coffee": what is possible

Short answer: **yes, as an in-app tip jar through Google Play Billing**, not as
a Buy Me a Coffee link inside the app.

- **Play's payments policy:** it generally requires in-app payments for
  digital goods, *including tips to the developer*, to go through Google Play
  Billing **(check)**. A button in the app that opens a Buy Me a Coffee page
  risks rejection. Some regions (US, EEA) now allow external links under
  special programs with their own fees, which adds complexity we don't need at
  launch.
- **Recommended:** a "Support PopUp Dictionary" screen with three one-time
  tips, e.g. *Small coffee* $0.99, *Large coffee* $2.99, *Breakfast* $4.99
  (prices are yours to choose). Nothing gets unlocked, it's a thank-you.
  Google keeps a 15% fee on your first US$1M per year.
  - Needs: the payments profile (step 3), the products created in Play Console,
    and the Google Play Billing library in the app (the first library besides
    the Claude SDK).
- **Your Buy Me a Coffee page** can still go on the GitHub README, a project
  website and social posts, just not as a payment button inside the Play
  version of the app.

## 5. Phases

1. **Prepare (about 1 week):** decisions in section 1, then the work in
   section 2, while your Play Console account is being verified.
2. **Closed test (14 days):** upload to a closed track and invite the 12+
   testers. Fix what they report.
3. **Production:** apply for production access, submit the listing, wait for
   review (often a few days for a new app).
4. **After launch:** features from `docs/feature-ideas.md` (Word of the Day,
   flashcards, word lists), then possibly a paid **Pro** tier so people can use
   the Claude features without their own API key. That's the bigger business
   opportunity, since most people won't create an Anthropic API key.

## 6. What I need from you

1. **Package name**: your preferred developer name or domain (e.g. `com.mimz.popupdictionary`).
2. **Publisher name** shown on Play (your name or a studio name).
3. **Public support email** for the listing and privacy policy.
4. **Tip prices and names** (or "use your suggestions").
5. **Your Buy Me a Coffee page link**, for the README and website.
6. Tell me when the **Play Console account** is ready, and I'll set up release signing.
