# Syndicate — five minute demo, voiceover script

**Video:** `syndicate-demo.mp4` (4:53, 1440×900, silent — this script is the audio)
**Pace:** about 770 words, written for roughly 155 words per minute. Timings are the real scene boundaries in the
recording, so you can read straight through against the picture.
**Tone:** explaining to someone who knows IPOs, not someone who knows software.

---

### 0:00 — Signing in
*On screen: the sign-in page, then the home screen.*

> Syndicate is diligence and disclosure control for public issues. Here's an SME IPO, run end to end.

---

### 0:06 — One answer starts a deal
*On screen: home, with "Start an IPO" and a single field.*

> Opening a deal asks one thing: the name of the company going public. The issue type, your role and
> the diligence areas all follow from who is asking.

---

### 0:12 — The transaction, and where it stands
*On screen: the transaction page, with the stage banner.*

> The stage isn't a field anyone maintains. It's worked out from what the transaction holds. Facts
> are being established, so it reads diligence — and it says what comes next.

---

### 0:21 — The workbench: what stands in the way
*On screen: scrolling through readiness, coverage counts, blocking items and known gaps.*

> The first screen answers one question: what stops this being filed with confidence right now?
> Not a percentage — counts. Material facts, how many verified, how many rest on documents.
>
> Then everything in the way: the fact nobody verified, the question nobody answered, the source
> that contradicts another. Each says what it concerns and what would clear it.

---

### 0:38 — The documents
*On screen: the Documents tab, the audited statements PDF, processing complete.*

> Documents arrive the way they always do. Each one is stored under its own hash, so the original is
> preserved and can be re-checked. Syndicate reads the text layer, falls back to OCR, and pulls out
> the figures it recognises.
>
> It treats none of them as true.

---

### 0:54 — The machine proposes, a person decides
*On screen: candidate values from the statement, each with the page region it came from; one is accepted.*

> This is the boundary the product is built on. What the extractor found is a candidate: a proposal,
> with the page region it came from, waiting for a person. Nothing becomes a fact of this
> transaction until someone accepts it.
>
> Accepting revenue creates a draft fact — and it still isn't verified. That takes a second person.

---

### 1:15 — A value you can follow back
*On screen: the fact page — value, status, who verified it, and the highlighted region in the PDF.*

> Once it's in, you can walk it back: who accepted it, who verified it, the document behind it, and
> the exact place on the page the number came from.

---

### 1:30 — When two sources disagree
*On screen: the Conflicts tab — 49% and 51% for overlapping periods — then a resolution is recorded.*

> Here's what makes this more than a document store. Two sources describe the promoter shareholding
> over the same period and disagree: forty-nine per cent on the September register, fifty-one on a
> statement dated the thirtieth.
>
> Syndicate won't choose. It shows both values, both documents, who recorded each, and when each was
> valid — and it blocks verification of either until a person settles it.
>
> The reviewer picks the value that stands and records why. The other isn't deleted. It's kept,
> marked rejected, with the reason attached.

---

### 2:04 — The questions a team already asks
*On screen: the Diligence tab, grouped by area; the site-visit question is answered with a document.*

> Every transaction opens with the questions an SME issue has to answer: fundraising history and
> where the money went, related-party counterparties, movements in inventory and receivables,
> litigation above the threshold, licence validity, the site visit.
>
> Not a checklist in a spreadsheet. An answer must point at the facts or documents it rests on, and
> it's accepted by someone other than whoever wrote it. Unanswered questions hold the filing back.

---

### 2:37 — Writing the document from verified facts
*On screen: a new disclosure; "Insert value" picks the capital figure.*

> The offer document comes last, and it's a derived artifact. Where a figure belongs in a sentence,
> you don't type it — you point at the fact. What's stored is a stable key, not a label someone
> might rename.

---

### 3:00 — Compiling the filing copy
*On screen: Compile final filing; a clean compile; the rendered section with the value as a chip.*

> Compiling checks the chain. Every value resolves to a human-verified fact, or the compile fails.
> Nothing stale, no unresolved reference, no unanswered blocking question, no open contradiction.
>
> What comes out traces every printed number back to its evidence.

---

### 3:21 — Proof of what produced it
*On screen: the provenance manifest drawer.*

> The filing copy carries its own proof: a manifest over every fact version, evidence hash and
> approval, recomputed as you open it — so a third party can confirm what produced this document.

---

### 3:34 — Approvals, and the package
*On screen: the Filing tab; the lead banker approves, the issuer admin has approved; the package is assembled.*

> Approval is given by named people in required roles, against one exact version. The lead banker
> signs; the issuer admin signs from their own account.
>
> Only then can the package be assembled: the document, an index of every document relied upon with
> its hash, the manifest, and a certificate of who approved what.
>
> Syndicate files nothing. It produces the package and the proof; filing stays with the people
> responsible for it.

---

### 4:05 — What a change would now cost
*On screen: pressing Correct on the capital figure; the impact notice lists four consequences.*

> Now the part that's hard by hand. Before changing an approved figure, the system says what the
> change reaches: the disclosure quoting it, the document that would stop matching, and both
> approvals, which would fall away. Four real things, from recorded dependencies.

---

### 4:23 — The record
*On screen: the audit trail.*

> All of it is on the record: who approved which version, who answered what, which value was kept in
> the conflict and why. The trail is append-only and hash-chained — the database refuses edits and
> deletions, and it outlives the transaction.

---

### 4:41 — Where the deal stands
*On screen: back to the overview; stage reads "Filing ready".*

> The deal is filing-ready, and what remains is visible rather than remembered. When the exchange
> comes back with a query, that's recorded here too.
>
> Syndicate: the transaction state underneath the offer document.

---

## Notes for whoever records the audio

- **Don't say "SEBI-compliant" or "approved".** The checks shipped with the product are configured
  checks written from secondary material; they are labelled internal policy in the interface and
  must be reviewed against the authoritative text before anyone claims otherwise. The product never
  says a regulator will approve anything.
- Numbers on screen are synthetic. "Shree Polymers Limited" is not a real issuer.
- If you re-record the video, scene boundaries move. `record-demo.mjs` prints the real timestamps of
  each scene as it runs; re-time the headings above from that output.

## Re-recording

The video is produced by driving the running product in a browser — nothing is mocked:

```bash
./start.sh                       # the product on http://localhost:8080
cd demo && npm install playwright # once
node record-demo.mjs .           # seeds a deal, records, prints scene timings
ffmpeg -i video/*.webm -c:v libx264 -crf 23 -pix_fmt yuv420p -movflags +faststart syndicate-demo.mp4
```

The script seeds the deal through the API first, so the recording spends its time on the parts worth
watching rather than on typing.
