# Syndicate --- Product & Engineering Bible

**Status:** Product and engineering specification\
**Date:** 21 September 2026\
**Primary market:** India\
**Initial transaction:** SME IPO\
**Primary customer hypothesis:** Lead merchant bankers / transaction
advisors and the professional teams executing SME public issues\
**Core problem:** Due-diligence evidence, material facts, regulatory
requirements, disclosures, and downstream dependencies are fragmented
across documents, spreadsheets, emails, people, and successive versions
of transaction information. Syndicate is intended to make that
information state explicit, traceable, reviewable, and change-aware.

------------------------------------------------------------------------

## 1. What Syndicate is

Syndicate is **diligence and disclosure-control infrastructure for
public-market transactions**.

The first transaction supported is an Indian SME IPO. The system is not
primarily a document-management application, an AI document generator, a
project-management application, or a chat application. Its purpose is to
maintain a structured, verified representation of the information that a
transaction team relies upon when preparing and reviewing an offer
document.

The central abstraction is the **substantiated transaction fact**.

A material statement in an IPO should not exist merely as text inside a
PDF. Syndicate should be able to represent the statement as structured
transaction state, identify the evidence supporting it, identify where
that evidence came from, record who reviewed and verified it, know which
regulatory requirement or diligence question it satisfies, know which
disclosures and calculations depend upon it, and know what becomes stale
when the underlying fact changes.

The offer document is therefore a **derived artifact**, not the system
of record.

The transaction team remains the authority. AI and deterministic
software are tools for finding, structuring, comparing, routing,
calculating, and validating information. They do not become the legal or
factual authority merely because a model produced an answer.

The product should make the following question easy to answer:

> **For every material statement that enters the transaction, why should
> the team believe it, where did it come from, who established it, what
> else depends on it, and what happens if it changes?**

That is the core of Syndicate.

------------------------------------------------------------------------

## 2. Why this problem exists

Public issues require extensive due diligence and disclosure. In India,
merchant bankers have formal due-diligence responsibilities, and SEBI
has continued to strengthen the record-keeping and review environment
around public issues.

SEBI issued a December 2024 circular requiring merchant bankers to
maintain documents relied upon during due diligence in an online
repository. The stated purpose is efficient maintenance of records and
documents relied upon while conducting due diligence in public issues.
The provisions apply to draft offer documents filed on or after 1
January 2025.

Source:
https://www.sebi.gov.in/legal/circulars/dec-2024/repository-of-documents-relied-upon-by-merchant-bankers-during-due-diligence-process-in-public-issues_89321.html

This creates an important distinction. A repository solves **where
documents are stored**. Syndicate should solve the higher-order problem
of **what those documents establish, how the evidence supports a
material statement, whether sources agree, who verified it, and what
downstream disclosures depend on it**.

BSE's guidance for SME IPO merchant bankers explicitly calls for
verification of past fundraising, valuation reports, bank statements,
utilization of funds, third-party vendors, litigation, auditor changes,
working-capital requirements, financial-performance changes, net worth,
leverage and related-party transactions. This is not a single-document
problem. It is a cross-source diligence problem.

Source:
https://www.bseindia.com/markets/MarketInfo/DownloadAttach.aspx?attachedId=088f3ab0-81f0-4b2a-bd2f-a0ab45aecce3&id=20241118-55

In September 2024, BSE cautioned merchant bankers after discrepancies
surfaced in SME IPO processes and highlighted issues including sudden
inventory changes, questionable fundraising objectives, lack of site
visits and ground checks, and the need for stronger due diligence.

Source:
https://www.financialexpress.com/market/bse-to-merchant-bankers-ensure-due-diligence-on-sme-ipos-3622166/

SEBI's enforcement record also demonstrates that due-diligence failures
can become regulatory matters. In October 2025, SEBI issued an enquiry
order concerning Gretex Corporate Services Limited in its capacity as
merchant banker.

Source:
https://www.sebi.gov.in/enforcement/orders/oct-2025/enquiry-order-in-the-matter-of-gretex-corporate-services-limited-merchant-banker_97563.html

BSE subsequently introduced a generative-AI pre-check facility for SME
IPO offer documents. The existence of that facility means that generic
"AI checks a DRHP for errors" is not a sufficient product thesis for
Syndicate. The product must operate beneath the document layer:
evidence, facts, dependencies, review state, and change impact.

Source:
https://www.bseindia.com/Downloads/MediaRelease/PR05082025a_20250508.pdf

Current SEBI processing reports also demonstrate that draft offer
documents remain active in a multi-stage regulatory process involving
issuers and coordinating lead managers.

Source:
https://www.sebi.gov.in/sebi_data/attachdocs/apr-2026/1777548100843.pdf

These sources establish the regulatory and operational environment. They
do **not** prove that every proposed Syndicate feature is commercially
demanded. Customer validation with merchant bankers, lawyers, auditors
and transaction advisors remains necessary.

------------------------------------------------------------------------

## 3. Product thesis

The core thesis is:

> **An IPO is not fundamentally a document-production problem. It is a
> controlled process for establishing, validating, reconciling,
> approving, and disclosing material information about an issuer and its
> transaction.**

Documents are inputs and outputs of that process.

The system should therefore maintain a transaction state composed of:

-   entities;
-   ownership and capital structure;
-   financial facts;
-   legal facts;
-   business facts;
-   regulatory facts;
-   material contracts;
-   litigation;
-   related parties;
-   promoters and management;
-   fundraising history;
-   issue structure;
-   objects of the issue;
-   diligence claims;
-   evidence;
-   requirements;
-   controls;
-   reviews;
-   approvals;
-   disclosures;
-   dependencies;
-   issues;
-   tasks;
-   changes;
-   audit records;
-   provenance.

The system should be able to move from raw evidence to an auditable,
human-verified transaction state without silently converting machine
output into truth.

------------------------------------------------------------------------

## 4. The central object model

The most important conceptual distinction in Syndicate is between
**source material, extracted candidate information, established facts,
claims, disclosures, and derived outputs**.

A document is not a fact.

An extraction is not a fact.

A model's answer is not a fact.

A human assertion is not automatically a verified fact.

A fact becomes authoritative for downstream transaction use only after
it crosses the explicit verification boundary defined by the system and
the relevant transaction role.

### 4.1 Evidence

Evidence is an original or otherwise preserved source artifact from
which information can be established.

Examples:

-   audited financial statement;
-   restated financial statement;
-   bank statement;
-   GST filing;
-   MCA filing;
-   board resolution;
-   certificate;
-   licence;
-   contract;
-   purchase order;
-   invoice;
-   valuation report;
-   auditor certificate;
-   litigation document;
-   property document;
-   email;
-   site-visit record;
-   government filing;
-   third-party report.

Evidence must preserve:

-   evidence ID;
-   original filename;
-   MIME type;
-   document type;
-   cryptographic hash;
-   source;
-   uploader;
-   organization;
-   transaction;
-   upload timestamp;
-   document date if known;
-   effective period if known;
-   processing state;
-   retention state;
-   version;
-   parent/source relationship if applicable;
-   access classification;
-   extracted text;
-   page representations where applicable;
-   extraction metadata.

Evidence is immutable in substance. A replacement or corrected source
creates a new version or a new evidence object; it must not silently
overwrite the old record.

### 4.2 Candidate Fact

A Candidate Fact is machine-derived or user-suggested information that
has **not yet become authoritative transaction state**.

A candidate contains:

-   candidate ID;
-   source evidence;
-   extracted label;
-   proposed value;
-   unit;
-   period;
-   source page;
-   bounding box;
-   extraction method;
-   confidence;
-   extraction run;
-   model/parser version;
-   reviewer status;
-   reviewer notes.

Candidate statuses:

-   `PENDING`;
-   `ACCEPTED`;
-   `REJECTED`;
-   `SUPERSEDED`.

Acceptance should create or propose a Fact. It must never silently
create a verified Fact.

### 4.3 Fact

A Fact is a structured assertion accepted into the transaction's
canonical state.

Examples:

-   promoter ownership = 51%;
-   revenue FY2026 = ₹42.18 crore;
-   paid-up capital = ₹12 crore;
-   licence expiry = 31 March 2028;
-   litigation amount = ₹8 crore;
-   related-party transaction amount = ₹1.2 crore;
-   EBITDA FY2026 = ₹6.4 crore.

A Fact must contain:

-   fact ID;
-   canonical field/semantic key;
-   display label;
-   typed value;
-   unit/currency;
-   entity subject;
-   period;
-   business validity interval;
-   system creation timestamp;
-   system supersession timestamp;
-   version;
-   status;
-   creator;
-   verifier;
-   verification timestamp;
-   confidence where relevant;
-   source/evidence links;
-   notes;
-   supersession relationship;
-   materiality;
-   sensitivity;
-   applicable workstream.

Facts must not be updated in place when a material value changes. A new
version is created and the old version becomes superseded.

### 4.4 Claim

A Claim is a higher-level statement that may be supported by one or more
facts and one or more pieces of evidence.

Examples:

-   "The company has not defaulted on its borrowings."
-   "The proposed object of the issue is supported by historical
    working-capital requirements."
-   "The promoter group has no undisclosed litigation above the
    materiality threshold."
-   "The proposed manufacturing expansion is supported by identified
    vendors and capacity requirements."

Claims are different from Facts because a claim may require multiple
pieces of evidence and reasoning.

A Claim should contain:

-   claim ID;
-   statement;
-   claim type;
-   materiality;
-   status;
-   responsible owner;
-   required evidence;
-   supporting facts;
-   supporting evidence;
-   review state;
-   challenge/exception state;
-   dependent disclosures;
-   audit history.

AI may propose Claims. Humans establish them.

### 4.5 Requirement

A Requirement represents something the transaction must satisfy,
disclose, evidence, review, or approve.

Requirements may originate from:

-   SEBI regulations;
-   SEBI master circulars;
-   exchange requirements;
-   applicable law;
-   transaction-specific conditions;
-   internal diligence policy;
-   professional-firm policy;
-   issuer-specific obligations.

A Requirement is data, not hard-coded prose.

It should contain:

-   requirement ID;
-   jurisdiction;
-   authority;
-   source citation;
-   source document;
-   effective-from;
-   effective-to;
-   transaction type;
-   applicability conditions;
-   required facts;
-   required evidence;
-   required reviews;
-   required approvals;
-   rule expression;
-   severity;
-   remediation;
-   version.

No requirement should be presented as regulatory truth without an
authoritative source and an effective date.

### 4.6 Control

A Control represents a deterministic or procedural check used to
establish that a requirement is satisfied.

Examples:

-   required financial statements exist;
-   promoter ownership reconciles across sources;
-   a licence remains valid through a defined date;
-   required approval exists;
-   a disclosure has evidence;
-   a required diligence question has been answered;
-   a material contract has been reviewed.

Controls may produce:

-   pass;
-   fail;
-   missing;
-   not applicable;
-   needs review;
-   exception.

Controls must be explainable.

### 4.7 Review

A Review is an explicit human evaluation event.

It records:

-   reviewer;
-   role;
-   scope;
-   target object;
-   decision;
-   comments;
-   timestamp;
-   version reviewed;
-   evidence considered;
-   follow-up requirements.

A review must attach to a concrete version of the object. A later change
invalidates or reopens the relevant review where appropriate.

### 4.8 Approval

An Approval is stronger than a review.

An approval states that an authorized person approved a specific
transaction state, document version, disclosure set, or decision.

Approvals must be version-bound.

A change to the approved object must not leave the approval valid
accidentally.

------------------------------------------------------------------------

## 5. The fundamental transaction graph

Syndicate should internally behave like a dependency graph even if
PostgreSQL remains the primary persistence technology.

The conceptual chain is:

**Evidence → Candidate Fact → Fact → Claim / Requirement / Control →
Review → Disclosure → Document Version**

There may be many-to-many relationships at every level.

A Fact can have multiple Evidence objects.

A Disclosure can depend on multiple Facts.

A Requirement can depend on multiple Facts and Evidence objects.

A Claim can depend on several Facts and Evidence objects.

A single Fact can affect many disclosures.

This graph is what enables change propagation.

The UI does not need to expose a literal graph database. PostgreSQL
relational tables plus explicit dependency tables are sufficient for the
initial implementation.

------------------------------------------------------------------------

## 6. The most important capability: change impact analysis

Change impact analysis is a core product capability, not a side effect.

When a material fact changes, Syndicate must determine:

1.  what fact changed;
2.  why it changed;
3.  which evidence supports the new value;
4.  which previous fact version was superseded;
5.  which conflicts are created or resolved;
6.  which requirements need reevaluation;
7.  which controls need reevaluation;
8.  which claims need review;
9.  which disclosures depend on the fact;
10. which calculations depend on the fact;
11. which document versions are invalidated;
12. which approvals become stale;
13. which people need to act;
14. which tasks should be created;
15. whether transaction readiness changes.

Example:

Promoter ownership changes from 51% to 49%.

Syndicate should identify that this affects the capital structure,
promoter disclosure, shareholding tables, potentially control-related
disclosures, potentially lock-in calculations, and any other registered
dependency. The system must not assume that only the original fact card
needs changing.

The system should present an **impact set**.

Example:

``` text
Changed fact:
Promoter ownership = 49%

Reason:
New board resolution + updated shareholding evidence

Conflicting source:
Previous shareholding statement = 51%

Affected:
- Capital structure requirement
- Promoter / promoter group disclosure
- Shareholding table
- Related disclosure X
- DRHP draft v7
- Review by Lead Lawyer
- Review by Lead Banker

State:
REVIEW REQUIRED
```

The actual dependencies must be determined from persisted relationships,
not hard-coded assumptions.

------------------------------------------------------------------------

## 7. Evidence integrity

Evidence is one of the most important foundations of the system.

### 7.1 Original preservation

The original uploaded file must be preserved.

The system must compute a cryptographic hash before or during
persistence and retain it permanently for the evidence lifecycle.

A changed file is a new version, not an overwrite.

### 7.2 Source location

For PDFs and images, extracted information should be anchored spatially
where possible:

-   page;
-   x;
-   y;
-   width;
-   height;
-   page dimensions;
-   extraction method.

The UI should allow the reviewer to click a fact and immediately see the
source region.

### 7.3 Evidence quality

Evidence should have a quality state:

-   `UNREVIEWED`;
-   `ACCEPTABLE`;
-   `INSUFFICIENT`;
-   `CONFLICTING`;
-   `STALE`;
-   `SUPERSEDED`;
-   `INVALID`.

Evidence quality is distinct from fact verification.

A fact can have evidence attached and still not be verified.

### 7.4 Evidence coverage

Syndicate should calculate coverage for important claims and
disclosures.

Examples:

-   97% of material facts have verified evidence.
-   3 material facts lack primary supporting evidence.
-   4 disclosures have only indirect support.
-   2 evidence sources conflict.
-   1 evidence package is stale.

This is more meaningful than "80% of documents uploaded."

------------------------------------------------------------------------

## 8. Diligence workbench

The primary operational interface should eventually become a diligence
workbench rather than a generic dashboard.

A transaction user should be able to see:

-   open diligence areas;
-   missing evidence;
-   unresolved conflicts;
-   unsupported material claims;
-   failed requirements;
-   stale disclosures;
-   pending reviews;
-   overdue tasks;
-   newly changed material facts;
-   regulatory observations;
-   document versions;
-   approval state.

The workbench should answer:

> What prevents this transaction from being confidently submitted right
> now?

Not:

> How many documents have we uploaded?

------------------------------------------------------------------------

## 9. Diligence questions

The system should model explicit diligence questions.

Examples:

-   What were all equity issuances during the relevant lookback period?
-   What was the consideration received for each issuance?
-   Where did the funds move after receipt?
-   Are any promoter/group entities counterparties?
-   Why did inventory increase materially?
-   Why did receivables increase materially?
-   Why did EBITDA change materially?
-   Are there pending litigations above the materiality threshold?
-   Are material licences valid through the relevant period?
-   Are major capex vendors financially and operationally credible?
-   What supports the stated object of the issue?
-   Has a required site visit occurred?
-   What evidence supports the site-visit conclusion?

Each question should have:

-   owner;
-   status;
-   answer;
-   evidence;
-   supporting facts;
-   review;
-   exceptions;
-   due date;
-   severity.

This converts an implicit human checklist into explicit transaction
state.

------------------------------------------------------------------------

## 10. Materiality

Not every fact deserves the same treatment.

Syndicate should support materiality levels:

-   `LOW`;
-   `NORMAL`;
-   `MATERIAL`;
-   `CRITICAL`.

Materiality can be assigned manually, derived by deterministic policy,
or suggested by AI.

The system must distinguish:

-   factual confidence;
-   evidence quality;
-   regulatory importance;
-   materiality.

These are separate concepts.

A low-confidence fact can be immaterial.

A highly certain fact can still be critical.

A material fact should have stronger review and change-propagation
rules.

------------------------------------------------------------------------

## 11. Conflict detection

Conflicts must be explicit.

The system must never silently choose between:

-   51% and 49%;
-   ₹42.18 crore and ₹41.8 crore;
-   two different dates;
-   two different capital amounts;
-   two different related-party classifications.

Conflict detection should compare facts using a semantic identity, not
only display labels.

A conflict key should include the relevant:

-   subject;
-   predicate;
-   period;
-   transaction context;
-   applicable version/state.

The system should distinguish genuine conflicts from legitimate temporal
differences.

For example:

Revenue FY2025 = ₹30 crore\
Revenue FY2026 = ₹42 crore

is not a conflict.

Revenue FY2026 = ₹42.18 crore\
Revenue FY2026 = ₹41.80 crore

may be a conflict.

The system must preserve both sources until a human resolves the
disagreement.

Resolution should record:

-   chosen fact;
-   rejected/obsolete fact;
-   reason;
-   reviewer;
-   evidence considered;
-   timestamp.

The system must not erase the losing source.

------------------------------------------------------------------------

## 12. Temporal model

Syndicate must distinguish two different kinds of time.

### Business validity time

When was the fact true in the business?

Example:

Promoter ownership: - 51% from 1 April to 31 August; - 49% from 1
September onward.

### System time

When did Syndicate learn and record the fact?

Example:

-   document uploaded on 10 September;
-   candidate extracted on 10 September;
-   fact accepted on 11 September;
-   fact verified on 12 September.

Historical reconstruction should therefore be possible.

A user should be able to ask:

> What did Syndicate believe on 15 September?

and separately:

> What was the ownership legally valid on 15 September?

These are different questions.

------------------------------------------------------------------------

## 13. Regulatory intelligence

Regulatory requirements must be modeled as versioned data.

Do not hard-code the entire regulatory framework into Java services.

Each requirement must have:

-   authority;
-   regulation/circular/master-circular reference;
-   source URL;
-   source document hash if stored;
-   effective date;
-   superseded date;
-   transaction type;
-   issue type;
-   applicability expression;
-   requirement text;
-   structured condition;
-   evidence requirement;
-   review requirement;
-   approval requirement;
-   severity;
-   implementation version.

The system must never silently use an old rule against a new
transaction.

A transaction should be able to pin the applicable regulatory-rule set.

Example:

``` text
Transaction:
SME IPO
Regulatory snapshot:
SEBI ICDR version X
BSE SME guidance version Y
Effective date:
2026-xx-xx
```

When a new rule becomes effective, the system should identify affected
transactions rather than silently rewriting historical conclusions.

### Important boundary

Syndicate is not a regulator.

A rule engine can state:

> "Based on the configured rule and current transaction data, this
> condition evaluates to FAIL."

It must not state:

> "SEBI will approve this IPO."

Likewise, AI must not declare legal compliance.

------------------------------------------------------------------------

## 14. Readiness

Readiness must be derived from actual blockers.

Do not use an arbitrary percentage.

Recommended states:

-   `NOT_READY`;
-   `CONDITIONALLY_READY`;
-   `READY_FOR_REVIEW`;
-   `READY_FOR_FILING`;
-   `STALE_AFTER_CHANGE`.

Readiness should consider:

-   unresolved critical conflicts;
-   missing required evidence;
-   failed regulatory controls;
-   unverified material facts;
-   stale disclosures;
-   missing required reviews;
-   missing approvals;
-   unresolved diligence questions;
-   required document sections;
-   transaction-specific blockers.

Every readiness decision should be explainable.

Example:

``` text
READY_FOR_REVIEW

Blocking issues: 0
Critical conflicts: 0
Required evidence coverage: 100%
Material facts verified: 100%
Required disclosures: complete
Required reviews: 92%
Pending approvals: 2

Next actions:
- Lead Lawyer review
- Lead Banker approval
```

------------------------------------------------------------------------

## 15. Disclosure system

Disclosures are derived representations of verified transaction state.

A disclosure should contain:

-   disclosure ID;
-   section;
-   title;
-   body/template;
-   status;
-   materiality;
-   fact dependencies;
-   claim dependencies;
-   requirement dependencies;
-   reviewer;
-   approval;
-   version;
-   stale reason.

A disclosure should be able to answer:

> Which facts produced this sentence?

and:

> If this fact changes, which disclosure becomes stale?

### Placeholder model

The current `{{fact:Label}}` mechanism can remain as a prototype, but
the production model should use stable semantic IDs rather than
human-readable labels.

Prefer:

``` text
{{fact:fact_123}}
```

or a semantic reference resolved through a disclosure dependency table.

Human-readable labels are presentation metadata, not stable identifiers.

### Compilation

Compilation should:

1.  resolve all dependencies;
2.  verify fact status;
3.  verify requirement state;
4.  detect stale dependencies;
5.  detect unresolved placeholders;
6.  detect conflicting facts;
7.  detect missing reviews;
8.  validate required sections;
9.  produce a deterministic compiled version;
10. record exact dependencies.

Compilation must fail closed for configured critical conditions.

------------------------------------------------------------------------

## 16. Document versions

Document versioning is required.

The system should distinguish:

-   source evidence version;
-   working disclosure version;
-   compiled DRHP version;
-   final filing package version.

A document version must have:

-   version number;
-   parent version;
-   created by;
-   created at;
-   source state;
-   content hash;
-   dependency snapshot;
-   approval snapshot;
-   compilation result;
-   lint result.

No final document should be considered independent of the transaction
state from which it was generated.

------------------------------------------------------------------------

## 17. Review and approval invalidation

Approvals and reviews must be bound to versions.

Suppose a lead banker approves DRHP v8.

Then promoter ownership changes.

DRHP v8 is now invalidated.

The system must not leave the old approval appearing to approve the new
state.

The new state must produce:

-   stale disclosure;
-   invalidated compiled document;
-   reopened review;
-   required reapproval where policy requires it.

This is a fundamental integrity property.

------------------------------------------------------------------------

## 18. Regulatory observation / query management

The product should eventually model exchange/regulator observations as
first-class transaction objects.

A regulatory observation should contain:

-   authority;
-   reference;
-   received date;
-   issue text;
-   section;
-   severity;
-   response owner;
-   response deadline;
-   linked disclosure;
-   linked facts;
-   supporting evidence;
-   response draft;
-   reviewer;
-   final response;
-   status.

The key feature is dependency tracking.

If an exchange asks:

> "Provide justification for the increase in receivables."

the system should connect that observation to:

-   receivables facts;
-   financial statements;
-   invoices;
-   customer ageing;
-   relevant disclosure;
-   previous diligence question;
-   response;
-   reviewer.

This prevents regulatory correspondence from becoming an isolated email
thread.

------------------------------------------------------------------------

## 19. Financial diligence

Financial information needs special treatment because it frequently
drives downstream disclosures and calculations.

The system should support structured financial facts for:

-   revenue;
-   EBITDA;
-   EBIT;
-   PAT;
-   assets;
-   liabilities;
-   net worth;
-   borrowings;
-   working capital;
-   inventory;
-   receivables;
-   payables;
-   cash;
-   capex;
-   issue proceeds;
-   utilization;
-   ratios;
-   share capital.

Every financial fact must include:

-   period;
-   currency;
-   unit;
-   accounting basis;
-   source statement;
-   source page;
-   audit/restatement status where applicable.

### Calculations

Calculated values should be distinguishable from sourced values.

Example:

``` text
Sourced:
Revenue FY2026 = ₹100 crore

Sourced:
Operating costs = ₹70 crore

Calculated:
EBITDA = ₹30 crore
```

A calculated fact should retain its formula and input fact versions.

If an input changes, the calculated fact becomes stale and is
recalculated.

Never silently overwrite a financial calculation.

------------------------------------------------------------------------

## 20. Capital structure and ownership

Capital structure should become a first-class domain model.

Do not represent all ownership information as generic Facts forever.

The system should eventually model:

-   security classes;
-   shareholders;
-   promoters;
-   promoter group;
-   share counts;
-   face value;
-   paid-up capital;
-   authorized capital;
-   issue price;
-   pre-issue holdings;
-   post-issue holdings;
-   transfers;
-   allotments;
-   ESOPs;
-   convertible instruments;
-   lock-in information;
-   fresh issue;
-   OFS;
-   dilution.

Ownership should be temporal.

A transaction should be able to answer:

> Who owned what immediately before the issue?

and:

> What will ownership look like after the proposed issue?

Every ownership calculation must be reproducible from underlying
transactions and verified inputs.

------------------------------------------------------------------------

## 21. Related parties

Related-party relationships should become explicit entities rather than
scattered facts.

Model:

-   party;
-   relationship type;
-   effective dates;
-   evidence;
-   transaction history;
-   related-party transaction;
-   amount;
-   period;
-   approval;
-   disclosure dependencies.

The system should identify possible related-party relationships from
evidence and flag them for human review.

AI may suggest.

Humans determine.

------------------------------------------------------------------------

## 22. Litigation

Litigation should be more structured than a generic issue.

Model:

-   case;
-   court/authority;
-   case number;
-   parties;
-   filing date;
-   current status;
-   amount;
-   contingent exposure;
-   materiality;
-   counsel;
-   source evidence;
-   disclosure;
-   risk treatment;
-   review state.

The system should support linking litigation to:

-   net worth;
-   materiality rules;
-   risk factors;
-   legal disclosures;
-   regulatory observations.

------------------------------------------------------------------------

## 23. Material contracts

Material contracts should be first-class objects.

Capture:

-   parties;
-   contract type;
-   effective date;
-   expiry;
-   termination;
-   value;
-   obligations;
-   exclusivity;
-   change-of-control provisions;
-   related-party status;
-   evidence;
-   reviewer;
-   disclosures.

A contract expiry or amendment should be able to trigger change-impact
analysis.

------------------------------------------------------------------------

## 24. Promoters and management

Model people and entities involved in:

-   promoters;
-   promoter group;
-   directors;
-   KMP;
-   senior management;
-   beneficial ownership;
-   related parties.

Identity should be consistent across transaction areas.

The same person should not become five unrelated records because five
documents spell their name differently.

Entity resolution can be assisted by AI, but final identity merges must
be human-controlled.

------------------------------------------------------------------------

## 25. External data and registry reconciliation

External registry data must be treated as another evidence source.

The current mock MCA adapter is only a development boundary.

Production architecture should support:

-   authenticated external connector;
-   source identity;
-   retrieval timestamp;
-   raw response;
-   response hash;
-   source version;
-   freshness;
-   rate limits;
-   failure states;
-   reconciliation.

When external data conflicts with issuer-provided data, the system
should create an explicit conflict.

It must not automatically declare the external registry correct in every
case.

------------------------------------------------------------------------

## 26. AI architecture

Syndicate should use AI aggressively where it creates operational
leverage, but **AI must not own transaction truth**.

### AI is appropriate for

-   document classification;
-   OCR correction;
-   entity extraction;
-   candidate fact extraction;
-   candidate claim extraction;
-   semantic matching;
-   duplicate detection;
-   anomaly detection;
-   conflict suggestions;
-   diligence question generation;
-   document summarization;
-   evidence retrieval;
-   regulatory-text parsing;
-   disclosure drafting;
-   response drafting;
-   natural-language search;
-   identifying potentially affected dependencies;
-   identifying missing evidence candidates.

### AI is not authoritative for

-   final financial facts;
-   legal conclusions;
-   regulatory compliance declarations;
-   approval;
-   permission grants;
-   evidence deletion;
-   audit modification;
-   final filing readiness;
-   choosing between conflicting material facts;
-   silently modifying verified state.

### Zero AI Authority

The hard boundary is:

**AI output → Candidate → Human review → Fact/Claim → Verification →
downstream use**

Never:

**AI output → Verified Fact**

The system must make this boundary visible in code and data structures.

------------------------------------------------------------------------

## 27. AI provenance

Every AI-generated artifact must record:

-   provider;
-   model;
-   model version if available;
-   prompt/template version;
-   input object IDs;
-   input hashes;
-   output;
-   timestamp;
-   confidence if applicable;
-   reviewer;
-   acceptance/rejection;
-   resulting object ID.

AI output without provenance should not enter a critical workflow.

------------------------------------------------------------------------

## 28. Search and retrieval

The product eventually needs transaction-wide search.

Users should be able to search:

-   evidence;
-   facts;
-   claims;
-   people;
-   companies;
-   requirements;
-   disclosures;
-   issues;
-   tasks;
-   regulatory observations.

Search results should show provenance and state.

Example query:

> "Show all evidence supporting working capital requirements."

The result should not merely return PDFs. It should return the relevant
evidence, facts, claims, disclosures, reviews and unresolved conflicts.

Natural-language search may use an LLM, but retrieved objects remain
authoritative.

------------------------------------------------------------------------

## 29. Human collaboration model

The initial roles should include:

-   Issuer Admin;
-   Promoter;
-   CFO;
-   Company Secretary;
-   Lead Banker;
-   Due Diligence Team;
-   Lead Lawyer;
-   Legal Associate;
-   Auditor;
-   Tax Advisor;
-   Regulatory Consultant;
-   Advisor.

The system must support organization-level and transaction-level
membership.

Access should be based on:

**User + Organization + Transaction + Role + Workstream + Permission**

Frontend visibility is not authorization.

Every sensitive operation must be enforced server-side.

------------------------------------------------------------------------

## 30. Permission principles

Permissions should be explicit.

Examples:

-   financial fact verification → authorized financial reviewer;
-   legal issue resolution → authorized legal role;
-   transaction activation → Issuer Admin + Lead Banker;
-   final filing approval → configured authorized roles;
-   evidence deletion → highly restricted;
-   audit mutation → prohibited;
-   regulatory-rule changes → privileged system administrator /
    regulatory-content role;
-   rule approval → separate from rule authoring where appropriate.

The system should avoid a giant "admin can do everything" model for
transaction-critical actions.

------------------------------------------------------------------------

## 31. Tasks and workflow

Tasks exist to resolve actual transaction state problems.

A task should normally originate from:

-   missing evidence;
-   failed requirement;
-   unresolved conflict;
-   stale disclosure;
-   regulatory observation;
-   missing review;
-   missing role;
-   change impact;
-   diligence question.

Manual tasks are allowed.

Generated tasks must be deterministic and idempotent.

Every generated task should link back to its reason.

Example:

``` text
Task:
Resolve promoter ownership conflict

Generated by:
CONFLICT: ownership:promoter:FY/current

Blocking:
YES

Inputs:
Evidence A → 51%
Evidence B → 49%

Owner:
Lead Banker

Affected:
4 disclosures
1 capital-structure calculation
1 pending review
```

------------------------------------------------------------------------

## 32. Notifications

Notifications should eventually support:

-   in-app;
-   email;
-   webhook;
-   digest.

Important events include:

-   assigned task;
-   escalation;
-   new conflict;
-   material fact change;
-   stale disclosure;
-   regulatory observation;
-   approval request;
-   approval invalidation;
-   readiness state change.

Notifications should never be the sole source of transaction truth. The
transaction state remains authoritative.

------------------------------------------------------------------------

## 33. Audit

Audit is not ordinary application logging.

Audit records should capture:

-   actor;
-   organization;
-   transaction;
-   action;
-   entity;
-   entity version;
-   old state reference;
-   new state reference;
-   reason;
-   timestamp;
-   request/correlation ID;
-   source channel;
-   relevant evidence.

The application should provide no ordinary update/delete path for audit
events.

For a production-grade regulated workflow, evaluate:

-   append-only storage;
-   WORM/immutable retention;
-   cryptographic chaining;
-   signed audit events;
-   independent audit export;
-   backup integrity;
-   access logging.

The current implementation records audit events but intentionally allows
primary business operations to continue if audit recording fails. That
is a prototype trade-off and must be revisited before regulated
production use.

------------------------------------------------------------------------

## 34. Provenance

Final filing provenance should be reproducible.

A final document version should identify:

-   exact document content;
-   fact versions;
-   evidence hashes;
-   requirement snapshot;
-   rule versions;
-   approvals;
-   reviewers;
-   compilation timestamp;
-   application version;
-   provenance manifest.

A Merkle-root design may be retained as a compact integrity proof.

The important property is not "blockchain."

The important property is:

> **A third party should be able to reconstruct what transaction state
> produced a particular filing artifact.**

Blockchain is not required.

------------------------------------------------------------------------

## 35. Filing package

The current compiler produces structured compiled content rather than a
complete production filing package.

The production system should eventually support:

-   document template;
-   section ordering;
-   tables;
-   footnotes;
-   cross references;
-   numbering;
-   citations;
-   formatting;
-   PDF;
-   DOCX where required;
-   final package;
-   evidence index;
-   diligence repository export;
-   provenance manifest;
-   approval certificate;
-   final hash.

The document-generation layer must remain downstream from verified
transaction state.

Do not let formatting logic become the system of record.

------------------------------------------------------------------------

## 36. Regulatory filing boundary

Syndicate should not initially attempt to become the filing portal
itself.

The initial product should produce a **filing-ready package** with a
complete audit/provenance trail.

Actual submission to exchanges or regulators should be introduced only
after:

-   legal requirements are confirmed;
-   authentication is understood;
-   certificates/signatures are supported;
-   submission interfaces are available;
-   regulatory responsibility is clear.

The system should not claim that a compiled document has been "filed"
unless an actual authoritative filing action occurred.

------------------------------------------------------------------------

## 37. Exchange/regulator response loop

After filing, Syndicate should continue to represent the transaction.

Lifecycle should support:

`DRAFT → ACTIVE → DUE_DILIGENCE → READINESS_REVIEW → DOCUMENT_PREPARATION → INTERNAL_APPROVAL → FILING_READY → FILED → OBSERVATIONS → RESPONSE → RESUBMISSION → ISSUE_PREPARATION → LISTED`

The exact lifecycle should remain configurable.

A regulatory observation should create a controlled change rather than a
new disconnected task list.

------------------------------------------------------------------------

## 38. Transaction state machine

A transaction is not simply a project container.

It represents a controlled state transition.

Every transition must have:

-   current state;
-   target state;
-   preconditions;
-   required approvals;
-   actor;
-   timestamp;
-   audit record.

A state transition must not bypass blockers unless an explicit exception
mechanism exists.

Exceptions must themselves be documented, authorized and auditable.

------------------------------------------------------------------------

## 39. Exceptions

Real transactions will contain exceptions.

The system needs an exception object rather than forcing users to
manipulate data to make a rule pass.

An exception contains:

-   requirement/control;
-   reason;
-   affected state;
-   supporting evidence;
-   risk classification;
-   owner;
-   approver;
-   expiry;
-   mitigation;
-   audit history.

A rule should never be bypassed silently.

------------------------------------------------------------------------

## 40. Current architecture baseline

The existing application is a Java/Spring Boot modular monolith with
React/Vite, PostgreSQL, RabbitMQ and Docker Compose.

The current implementation already contains:

-   organizations;
-   companies;
-   transactions;
-   transaction roles;
-   workstreams;
-   evidence;
-   SHA-256 evidence hashes;
-   PDF text extraction;
-   Tesseract OCR fallback;
-   spatial candidate facts;
-   human candidate acceptance;
-   immutable fact versions;
-   business/system temporal fields;
-   conflict detection;
-   issues;
-   tasks;
-   deterministic readiness rules;
-   disclosure dependencies;
-   stale propagation;
-   DRHP compilation;
-   audit;
-   mock external registry verification;
-   provenance/Merkle manifest.

The current implementation is explicitly a prototype and does not yet
contain all the domain entities described here.

Do not rewrite the architecture merely because the domain model is
expanding.

The modular monolith is appropriate while product-market fit is unknown.

------------------------------------------------------------------------

## 41. Engineering principles

### Principle 1: Preserve the source

Never destroy evidence merely to simplify state.

### Principle 2: Separate observation from truth

An extracted candidate is an observation.

A verified fact is transaction state.

### Principle 3: Never silently resolve material conflict

Conflicts must remain visible until resolved.

### Principle 4: Version material state

Never overwrite critical facts.

### Principle 5: Make dependencies explicit

If an output depends on a fact, store the dependency.

### Principle 6: Invalidate downstream state when inputs change

Do not allow stale approvals or disclosures to look current.

### Principle 7: Deterministic systems control critical decisions

Use deterministic code for permissions, state transitions, formulas,
rules, versioning and approval validity.

### Principle 8: AI proposes; humans establish

AI is an acceleration layer, not a source of legal authority.

### Principle 9: Every important decision must be explainable

A reviewer must be able to understand why the system reached a state.

### Principle 10: Historical state must be reproducible

The system should reconstruct what was known and approved at a
particular point in time.

### Principle 11: Documents are derived artifacts

Do not store critical truth only in generated prose.

### Principle 12: Regulatory logic must be versioned

A rule applicable today may not have been applicable historically.

------------------------------------------------------------------------

## 42. Product non-goals

Syndicate is not initially:

-   a generic CRM;
-   a generic project-management tool;
-   a generic virtual data room;
-   a generic RAG chatbot;
-   an AI legal advisor;
-   an autonomous IPO agent;
-   an exchange;
-   a regulator;
-   a filing portal;
-   a generic accounting system;
-   a general-purpose ERP;
-   a marketplace for IPO advisors;
-   a consumer investment application;
-   a blockchain product.

These systems may integrate with Syndicate later.

They are not the core.

------------------------------------------------------------------------

## 43. What Syndicate must NOT become

Do not add features simply because they are technically impressive.

Avoid:

-   generic chat everywhere;
-   autonomous agents changing verified facts;
-   AI-generated "compliance scores" without rule provenance;
-   blockchain without a concrete trust requirement;
-   microservices before scale requires them;
-   a custom graph database before relational dependency modeling
    becomes insufficient;
-   enormous BI dashboards;
-   generic document storage without transaction semantics;
-   "AI DRHP writer" positioning;
-   arbitrary readiness percentages;
-   untraceable model-generated summaries;
-   silently replacing source documents;
-   automatic conflict resolution for material data.

The product should become **more rigorous**, not merely larger.

------------------------------------------------------------------------

## 44. Core user workflows

### Workflow A: New transaction

1.  Create issuer/company.
2.  Create transaction.
3.  Assign lead organization.
4.  Invite transaction participants.
5.  Configure transaction type.
6.  Select applicable regulatory rule set.
7.  Create workstreams.
8.  Establish transaction baseline.
9.  Start evidence collection.

### Workflow B: Evidence ingestion

1.  Upload evidence.
2.  Calculate hash.
3.  Store original.
4.  Classify document.
5.  Extract text.
6.  OCR where necessary.
7.  Extract candidate facts/claims.
8.  Anchor candidates to source locations.
9.  Present candidates to reviewer.
10. Accept/reject.
11. Create Fact/Claim only through explicit human action.

### Workflow C: Fact verification

1.  Review candidate.
2.  Inspect source.
3.  Compare other sources.
4.  Accept into Fact.
5.  Link evidence.
6.  Assign materiality.
7.  Verify.
8.  Run conflict detection.
9.  Run dependent controls.
10. Propagate changes.

### Workflow D: Conflict

1.  Detect competing values.
2.  Create conflict issue.
3.  Show all sources.
4.  Assign responsible role.
5.  Reviewer investigates.
6.  Reviewer selects current interpretation.
7.  Record reason.
8.  Supersede/retain facts as appropriate.
9.  Re-evaluate requirements.
10. Revalidate dependent disclosures.

### Workflow E: Disclosure

1.  Create section.
2.  Link facts/claims.
3.  Draft content.
4.  Run disclosure lint.
5.  Review.
6.  Approve.
7.  Compile.
8.  Create immutable document version.
9.  Record provenance.

### Workflow F: Material change

1.  New evidence arrives.
2.  Candidate created.
3.  New Fact proposed.
4.  Existing Fact conflict detected.
5.  Impact graph traversed.
6.  Dependent disclosures become stale.
7.  Calculations become stale.
8.  Reviews become stale where required.
9.  Tasks generated.
10. Readiness reevaluated.
11. New document version compiled after resolution.

### Workflow G: Regulatory observation

1.  Observation recorded.
2.  Requirement/section linked.
3.  Facts and evidence linked.
4.  Response task generated.
5.  Draft response created.
6.  Reviewer checks response.
7.  Response approved.
8.  Disclosure/facts updated if necessary.
9.  New compilation generated.
10. Complete history retained.

------------------------------------------------------------------------

## 45. Data model target

The following entities should exist as the domain matures:

### Identity

-   User
-   Organization
-   OrganizationMembership
-   Permission
-   Role

### Transaction

-   Company
-   CompanyRelationship
-   Transaction
-   TransactionMembership
-   Workstream
-   TransactionState
-   TransactionStateTransition

### Evidence

-   Evidence
-   EvidenceVersion
-   EvidenceLocation
-   ExtractionRun
-   CandidateFact
-   CandidateClaim
-   AIArtifact

### Truth

-   Fact
-   FactVersion
-   FactEvidence
-   FactRelationship
-   Claim
-   ClaimFact
-   ClaimEvidence

### Compliance

-   Requirement
-   RequirementVersion
-   Control
-   ControlEvaluation
-   RegulatoryRule
-   RegulatoryRuleVersion
-   Exception

### Review

-   Review
-   Approval
-   ApprovalRequirement
-   ApprovalInvalidation

### Diligence

-   DiligenceQuestion
-   DiligenceAnswer
-   DiligenceReview
-   MaterialityAssessment

### Transaction entities

-   Security
-   Shareholder
-   OwnershipPosition
-   OwnershipEvent
-   Promoter
-   Director
-   KMP
-   RelatedParty
-   RelatedPartyTransaction
-   MaterialContract
-   Litigation
-   RegulatoryObservation

### Output

-   Disclosure
-   DisclosureVersion
-   DisclosureDependency
-   Document
-   DocumentVersion
-   Compilation
-   FilingPackage

### Work management

-   Issue
-   Conflict
-   Task
-   Notification

### Integrity

-   AuditEvent
-   ProvenanceManifest
-   ExternalRegistrySnapshot

The system does not need to implement every entity at once.

The model should evolve toward this structure as validated workflows
justify each object.

------------------------------------------------------------------------

## 46. API principles

All APIs must be transaction-scoped where appropriate.

Prefer:

``` text
/transactions/{transactionId}/facts
```

over globally ambiguous resources where transaction context is required.

Every mutation should:

-   validate authorization;
-   validate object version/state;
-   validate business invariants;
-   write audit event;
-   emit domain event where needed;
-   trigger deterministic downstream processing;
-   return the resulting version/state.

Critical mutations should support idempotency where duplicate requests
are possible.

------------------------------------------------------------------------

## 47. Domain events

The system should gradually introduce explicit domain events.

Examples:

-   `EvidenceUploaded`
-   `EvidenceProcessed`
-   `CandidateFactCreated`
-   `FactAccepted`
-   `FactVerified`
-   `FactSuperseded`
-   `FactConflictDetected`
-   `ConflictResolved`
-   `RequirementFailed`
-   `RequirementPassed`
-   `DisclosureMarkedStale`
-   `ReviewCompleted`
-   `ApprovalGranted`
-   `ApprovalInvalidated`
-   `RegulatoryObservationCreated`
-   `TransactionStateChanged`
-   `CompilationCreated`

Events should carry stable object IDs and versions.

Do not put large mutable payloads in events when references are
sufficient.

------------------------------------------------------------------------

## 48. Idempotency

All asynchronous workflows must be idempotent.

Examples:

-   extraction;
-   conflict detection;
-   readiness evaluation;
-   generated tasks;
-   notification generation;
-   document compilation.

The same event must not create duplicate tasks or duplicate facts.

Use stable idempotency keys based on:

-   transaction;
-   source object;
-   operation;
-   version.

------------------------------------------------------------------------

## 49. Asynchronous processing

RabbitMQ remains appropriate for:

-   OCR;
-   extraction;
-   classification;
-   large document processing;
-   background conflict analysis;
-   readiness recalculation;
-   indexing.

Critical state transitions should remain transactionally safe.

Do not use asynchronous processing to hide business-state races.

A fact verification must not return success before the fact's
authoritative state is committed.

Derived downstream work can be asynchronous.

------------------------------------------------------------------------

## 50. Storage architecture

PostgreSQL remains the system of record for structured transaction
state.

Object storage should eventually replace local filesystem evidence
storage for production.

Required properties:

-   encryption at rest;
-   encryption in transit;
-   immutable/versioned objects;
-   retention policy;
-   legal hold;
-   backup;
-   lifecycle policy;
-   access logs;
-   malware scanning;
-   content-type validation.

Search/indexing can be added separately.

A graph database is not required initially.

------------------------------------------------------------------------

## 51. Security architecture

Production requirements include:

-   secure secret management;
-   TLS;
-   short-lived access tokens;
-   secure refresh/session model;
-   MFA for privileged roles;
-   rate limiting;
-   brute-force protection;
-   account recovery;
-   email verification;
-   organization isolation;
-   transaction authorization;
-   object-level authorization;
-   audit logging;
-   secure file upload;
-   malware scanning;
-   resource limits;
-   SSRF protection for external retrieval;
-   safe PDF/image processing;
-   dependency scanning;
-   security headers;
-   CSP;
-   secure cookie/session handling where applicable.

Evidence may contain highly sensitive corporate information.

Assume evidence is confidential by default.

------------------------------------------------------------------------

## 52. Multi-tenancy

Organization boundaries are mandatory.

A user in Organization A must not retrieve evidence, facts, tasks,
disclosures, or transactions belonging to Organization B unless explicit
transaction participation permits it.

Transaction membership must be evaluated on every protected transaction
resource.

Avoid relying on frontend filtering.

Database-level row-level security can be evaluated later, but
service-level authorization must be comprehensive even before RLS
exists.

------------------------------------------------------------------------

## 53. Data retention

The system needs a formal evidence-retention policy.

Do not expose a generic "delete evidence" operation without considering:

-   regulatory retention;
-   transaction state;
-   legal hold;
-   audit requirements;
-   provenance dependencies;
-   document repository requirements.

If evidence is referenced by a final filing package, deletion should
normally be blocked or converted into a controlled archival state.

------------------------------------------------------------------------

## 54. Observability

Production infrastructure should expose:

-   request metrics;
-   error rates;
-   queue depth;
-   extraction duration;
-   OCR duration;
-   failed extraction jobs;
-   readiness evaluation duration;
-   compilation duration;
-   database health;
-   storage failures;
-   external connector failures;
-   audit write failures;
-   notification failures.

Every background operation should have:

-   correlation ID;
-   transaction ID;
-   object ID;
-   operation ID.

------------------------------------------------------------------------

## 55. Testing strategy

The product is too stateful to rely on unit tests alone.

Required test layers:

### Unit

-   fact versioning;
-   temporal queries;
-   conflict logic;
-   rule evaluation;
-   dependency traversal;
-   stale propagation;
-   calculation engine;
-   permission policies;
-   provenance hashing.

### Integration

-   PostgreSQL;
-   RabbitMQ;
-   object storage;
-   OCR;
-   extraction;
-   event processing;
-   transaction boundaries.

### Security

Every role against every protected endpoint.

Test:

-   cross-organization access;
-   cross-transaction access;
-   evidence access;
-   evidence deletion;
-   fact verification;
-   issue resolution;
-   task mutation;
-   approval;
-   final compilation.

### End-to-end

At minimum:

1.  create organization;
2.  create company;
3.  create transaction;
4.  invite roles;
5.  upload evidence;
6.  extract candidate;
7.  accept candidate;
8.  verify fact;
9.  create conflict;
10. resolve conflict;
11. satisfy requirement;
12. create disclosure;
13. compile;
14. change source fact;
15. verify stale propagation;
16. re-review;
17. compile again;
18. inspect provenance.

### Regression invariant tests

The following must always remain true:

-   candidate cannot directly become verified;
-   stale disclosure cannot compile as clean;
-   superseded fact cannot silently supply final output;
-   unresolved critical conflict blocks configured output;
-   unauthorized role cannot verify restricted fact;
-   old approval cannot approve new version;
-   deleted evidence cannot remain falsely represented as available
    evidence;
-   historical state remains reconstructable.

------------------------------------------------------------------------

## 56. Engineering quality gates

Before calling a release production-capable:

-   backend tests pass;
-   frontend tests pass;
-   integration tests pass;
-   security tests pass;
-   migrations are reproducible;
-   backups are tested;
-   object storage recovery is tested;
-   queue failure/replay is tested;
-   audit integrity is tested;
-   provenance is reproducible;
-   authorization matrix is tested;
-   regulatory rules are source-linked;
-   critical workflows are observable.

------------------------------------------------------------------------

## 57. Regulatory-content governance

Regulatory content is code-adjacent and must be treated accordingly.

Each regulatory rule must have:

-   source;
-   source version;
-   effective date;
-   author;
-   reviewer;
-   approval;
-   test cases;
-   applicability;
-   supersession relationship.

A rule should not be edited in place after use by a transaction.

Create a new version.

Historical transactions retain the rule snapshot used for their
decision.

------------------------------------------------------------------------

## 58. Product metrics

Do not optimize for:

-   documents uploaded;
-   AI calls;
-   chatbot messages;
-   number of generated pages.

The important product metrics are:

### Diligence efficiency

-   time from evidence upload to accepted fact;
-   time from conflict detection to resolution;
-   time to answer diligence question;
-   time to prepare a filing-ready package.

### Quality

-   number of material conflicts found before filing;
-   number of unsupported material claims;
-   number of stale disclosures caught before submission;
-   percentage of material claims with primary evidence;
-   percentage of required controls with explicit evidence.

### Change management

-   number of downstream objects automatically identified after material
    change;
-   time to revalidate affected disclosures;
-   number of stale approvals caught.

### Customer value

-   analyst hours saved;
-   reduction in repetitive reconciliation;
-   reduction in document-review cycles;
-   reduction in missing-evidence requests;
-   reduction in exchange/regulatory query turnaround.

The strongest commercial metric may eventually be:

> **How many hours of senior transaction-team time does Syndicate remove
> from each IPO without reducing diligence quality?**

------------------------------------------------------------------------

## 59. Customer hypothesis

The primary customer hypothesis is not necessarily the SME itself.

The most promising recurring customer class is:

-   SME-focused merchant bankers;
-   lead managers;
-   transaction advisors;
-   capital-markets law firms;
-   professional diligence teams.

An issuer may use Syndicate as a participant, but the recurring
operational value is likely strongest for organizations executing
multiple transactions.

This is a hypothesis to validate, not an established conclusion.

------------------------------------------------------------------------

## 60. Customer discovery questions

Before major product expansion, interview:

-   lead merchant bankers;
-   SME IPO transaction teams;
-   IPO lawyers;
-   auditors;
-   company secretaries;
-   transaction advisors.

Ask about actual behavior, not whether they "like the idea."

Useful questions:

1.  Walk me through the last SME IPO you executed.
2.  Where did source documents live?
3.  How did you track what each document established?
4.  How did you reconcile the same number appearing in different
    documents?
5.  What happens when a number changes after the first DRHP?
6.  How do you know which disclosures need updating?
7.  How do you prove what evidence was relied upon?
8.  How do you track diligence questions?
9.  How do you track unanswered questions?
10. What causes an exchange query?
11. What takes the most analyst time?
12. What gets done repeatedly in Excel?
13. What gets done repeatedly by email?
14. Which work is hardest to audit later?
15. What information is lost between legal, financial and
    merchant-banking teams?
16. What happens when the issuer gives contradictory information?
17. How are document versions handled?
18. How are regulatory observations tracked?
19. What software do you already use?
20. What would make you replace it?

Ask for examples from a real completed transaction.

Do not validate the product by asking:

> "Would you use Syndicate?"

Validate by observing current workflow and quantified pain.

------------------------------------------------------------------------

## 61. Product wedge

The initial wedge should be:

> **Evidence-backed diligence and change-impact control for SME IPO
> transactions.**

The first valuable experience should be:

> Upload transaction evidence → establish structured facts → reconcile
> conflicts → show evidence → map facts to disclosures/requirements →
> detect changes → identify everything affected.

The DRHP compiler is downstream.

The task board is downstream.

The chat interface is optional.

The core is transaction truth and its dependencies.

------------------------------------------------------------------------

## 62. Competitive positioning

Do not compete by claiming that nobody else manages IPO documents.

There are already:

-   virtual data rooms;
-   transaction-management platforms;
-   document collaboration systems;
-   legal workflow platforms;
-   IPO document-review tools;
-   exchange-side validation systems.

Syndicate should differentiate through the **structured relationship
between evidence, verified transaction facts, requirements, reviews,
disclosures and change impact**.

The product claim should therefore be closer to:

> "We don't just store the documents behind an IPO. We maintain the
> verified transaction state that those documents establish and show how
> every material change affects the filing."

This must be validated commercially.

------------------------------------------------------------------------

## 63. Why the document generator is not the core

A document generator can produce text.

The difficult part is determining:

-   which fact is current;
-   whether it is supported;
-   whether it conflicts;
-   whether it is material;
-   whether it satisfies a requirement;
-   whether it has been reviewed;
-   whether it changed after approval;
-   what else depends on it.

Therefore:

**Generation is the final mile.**

**State integrity is the product.**

------------------------------------------------------------------------

## 64. Why a generic "AI agent" is not the core

An autonomous agent is dangerous in this domain if it can mutate
critical state without explicit authorization.

The correct architecture is a controlled agent environment:

AI can:

-   inspect;
-   search;
-   summarize;
-   propose;
-   compare;
-   extract;
-   identify;
-   draft;
-   explain.

The system decides whether the proposed action is permitted.

Humans authorize material state changes.

------------------------------------------------------------------------

## 65. Example: ownership conflict

Consider this complete transaction:

Evidence A:

``` text
Shareholding statement
Promoter ownership: 51%
Period: 30 June 2026
```

Evidence B:

``` text
Updated board resolution
Ownership restructuring
Effective: 1 September 2026
```

Evidence C:

``` text
Updated shareholder record
Promoter ownership: 49%
```

Syndicate should represent:

``` text
Fact v1:
51%
valid_to:
31 August 2026
verified:
yes

Fact v2:
49%
valid_from:
1 September 2026
verified:
pending
```

There is no contradiction if the periods are correctly understood.

Now suppose another document says:

``` text
Promoter ownership as of 30 September 2026:
51%
```

The system should create a conflict.

It should not choose 49%.

It should not choose 51%.

It should show:

-   source A;
-   source B;
-   source C;
-   page locations;
-   dates;
-   uploaders;
-   reviewers;
-   verification states;
-   affected disclosures;
-   affected calculations;
-   responsible owner.

After resolution, the reviewer records the decision.

That is the behavior that distinguishes Syndicate from a document
repository.

------------------------------------------------------------------------

## 66. Example: financial change

Suppose:

``` text
Revenue FY2026:
₹42.18 crore
```

Later, a restated financial statement produces:

``` text
Revenue FY2026:
₹41.80 crore
```

Syndicate must know:

-   which statement introduced the new value;
-   page;
-   financial period;
-   audit/restatement state;
-   who uploaded it;
-   who accepted it;
-   who verified it;
-   what calculations use revenue;
-   what disclosures use revenue;
-   whether a financial ratio changes;
-   whether a regulatory control changes;
-   whether a previously approved disclosure is now stale.

The product value is not merely detecting 42.18 versus 41.80.

The product value is **knowing the consequences of the change**.

------------------------------------------------------------------------

## 67. Example: evidence insufficiency

Suppose the company claims:

> "IPO proceeds will fund expansion of manufacturing capacity."

Syndicate should be able to connect the claim to:

-   proposed capex;
-   vendor quotations;
-   capacity assumptions;
-   historical production;
-   current utilization;
-   site information;
-   financial projections;
-   board approval;
-   relevant disclosure.

If the evidence is incomplete, the system should identify exactly what
is missing.

The goal is not to decide whether the project is commercially sensible.

The goal is to expose whether the transaction's stated claim is
sufficiently substantiated for the configured diligence process.

------------------------------------------------------------------------

## 68. Example: regulatory observation

Observation:

> "Explain the increase in inventory during FY2026."

Syndicate should connect the observation to:

-   inventory fact;
-   historical inventory facts;
-   revenue facts;
-   purchase records;
-   production data;
-   working-capital analysis;
-   relevant disclosure;
-   diligence question;
-   response;
-   reviewer.

When the response is approved, the system should preserve the exact
evidence and transaction state behind it.

------------------------------------------------------------------------

## 69. Implementation roadmap

### Phase A --- Integrity foundation

Prioritize:

-   repair backend tests;
-   strengthen authorization;
-   durable evidence storage;
-   evidence versioning;
-   robust audit;
-   fact versioning;
-   stable semantic identifiers;
-   dependency tables;
-   change events;
-   object version checks.

### Phase B --- Diligence control

Build:

-   diligence questions;
-   evidence requirements;
-   evidence coverage;
-   materiality;
-   claim model;
-   review model;
-   structured conflict workflow;
-   change impact engine.

### Phase C --- Transaction domain depth

Build:

-   cap table;
-   ownership;
-   promoters;
-   related parties;
-   management;
-   litigation;
-   material contracts;
-   fundraising history;
-   financial model/facts.

### Phase D --- Regulatory engine

Build:

-   versioned regulatory requirements;
-   applicability;
-   control definitions;
-   rule authoring;
-   rule testing;
-   source provenance;
-   regulatory snapshots;
-   exceptions.

### Phase E --- Disclosure control

Build:

-   stable disclosure dependencies;
-   disclosure versions;
-   section templates;
-   impact analysis;
-   review/approval invalidation;
-   structured document generation.

### Phase F --- Filing and observation loop

Build:

-   filing package;
-   regulatory observations;
-   response workflow;
-   resubmission tracking;
-   final provenance package.

### Phase G --- Intelligence

Only after the state model is stable:

-   AI extraction;
-   semantic search;
-   anomaly detection;
-   diligence assistant;
-   natural-language transaction queries;
-   agentic workflow suggestions.

AI should improve the system, not define the system.

------------------------------------------------------------------------

## 70. What to build next

The immediate development priority should not be another dashboard.

The highest-value engineering work is to turn the existing
Fact/Evidence/Disclosure implementation into a genuine dependency-aware
transaction state system.

Specifically:

1.  introduce stable semantic identifiers for facts;
2.  introduce explicit FactVersion records if the existing model becomes
    insufficient;
3.  introduce Claim;
4.  introduce Requirement/RequirementVersion;
5.  introduce Control/ControlEvaluation;
6.  introduce Review;
7.  introduce Approval/ApprovalInvalidation;
8.  introduce explicit DisclosureDependency;
9.  introduce DiligenceQuestion;
10. introduce MaterialityAssessment;
11. implement a generic dependency graph over relational tables;
12. implement change-impact traversal;
13. implement stale propagation across facts, claims, controls,
    disclosures and approvals;
14. build the diligence workbench;
15. strengthen evidence custody;
16. make regulatory rules source/version/effective-date aware.

Do not build all of these blindly. Each should be tied to a validated
customer workflow.

------------------------------------------------------------------------

## 71. Definition of done for the core product

The core product is successful when a merchant banker can take a
transaction and answer, from one system:

> What do we know?

> Why do we believe it?

> Where is the evidence?

> Who verified it?

> What conflicts exist?

> What requirements are unsatisfied?

> What material claims are unsupported?

> Which disclosures depend on this fact?

> What changed since the last review?

> What became stale because of that change?

> Who needs to act?

> What exactly was approved?

> What transaction state produced this filing?

If the system cannot answer these questions reliably, it is not yet the
intended product.

------------------------------------------------------------------------

## 72. Final product definition

Syndicate is **not software that writes an IPO document**.

Syndicate is **the transaction-control layer underneath the IPO
document**.

It creates a durable, structured and auditable representation of:

-   what the company says;
-   what the evidence says;
-   what the transaction team has accepted;
-   what has been independently verified;
-   what remains disputed;
-   what regulatory requirements apply;
-   what disclosures depend on which facts;
-   what changed;
-   what needs to be reviewed again;
-   what was approved;
-   and what exact state produced the final filing artifact.

The system should make the transaction's information state explicit.

The operating principle is:

> **Evidence establishes candidate information. Humans establish
> authoritative facts and claims. Deterministic controls evaluate
> requirements. Dependencies propagate change. Reviews and approvals
> establish responsibility. Documents are derived outputs. Audit and
> provenance preserve history.**

That is the product.

------------------------------------------------------------------------

## 73. Non-negotiable invariants

These invariants must survive every future architectural change.

1.  **AI output is never authoritative by itself.**
2.  **Candidate facts require explicit human acceptance.**
3.  **Critical facts require appropriate verification.**
4.  **Material facts are versioned, never silently overwritten.**
5.  **Evidence is preserved and traceable.**
6.  **Every material disclosure can identify its dependencies.**
7.  **Every material fact can identify its evidence.**
8.  **Conflicts are explicit and never silently resolved.**
9.  **Changes propagate to dependent state.**
10. **Stale outputs cannot masquerade as current outputs.**
11. **Approvals are bound to versions.**
12. **Regulatory rules are versioned by source and effective date.**
13. **Historical transaction state remains reconstructable.**
14. **Permissions are enforced server-side.**
15. **Audit records are not ordinary mutable business data.**
16. **Final provenance is reproducible.**
17. **Documents are derived artifacts, not the source of truth.**
18. **The system never claims regulatory approval merely because an
    internal rule passed.**
19. **Exceptions are explicit, authorized and auditable.**
20. **Every important automated decision must have an explainable
    reason.**

------------------------------------------------------------------------

## 74. Source material and regulatory references

The following sources were used to establish the regulatory/operational
context for this product specification. They should be rechecked before
implementing regulatory logic because regulations and exchange
procedures can change.

### SEBI

Repository of documents relied upon by Merchant Bankers during due
diligence in Public Issues --- 5 December 2024:

https://www.sebi.gov.in/legal/circulars/dec-2024/repository-of-documents-relied-upon-by-merchant-bankers-during-due-diligence-process-in-public-issues_89321.html

SEBI processing status of draft offer documents --- 30 April 2026:

https://www.sebi.gov.in/sebi_data/attachdocs/apr-2026/1777548100843.pdf

SEBI Merchant Banker regulatory materials:

https://www.sebi.gov.in/sebiweb/home/HomeAction.do?doListingAll=yes&search=Merchant+Banker

SEBI enforcement order concerning Gretex Corporate Services Limited ---
30 October 2025:

https://www.sebi.gov.in/enforcement/orders/oct-2025/enquiry-order-in-the-matter-of-gretex-corporate-services-limited-merchant-banker_97563.html

SEBI master circulars, including the 2026 Merchant Bankers and ICDR
master circulars:

https://www.sebi.gov.in/sebiweb/home/HomeAction.do?doListing=yes&sid=1&ssid=6

### BSE

Guidance to Merchant Bankers for Preparation of Offer Documents --- SME
IPO:

https://www.bseindia.com/markets/MarketInfo/DownloadAttach.aspx?attachedId=088f3ab0-81f0-4b2a-bd2f-a0ab45aecce3&id=20241118-55

BSE 2025 media release describing its generative-AI pre-check facility
for SME IPO documents:

https://www.bseindia.com/Downloads/MediaRelease/PR05082025a_20250508.pdf

### External reporting used for problem validation

Financial Express report on BSE's 2024 warning to merchant bankers
regarding SME IPO due diligence:

https://www.financialexpress.com/market/bse-to-merchant-bankers-ensure-due-diligence-on-sme-ipos-3622166/

Moneycontrol report on BSE's 2025 AI pre-check facility:

https://www.moneycontrol.com/news/business/markets/bse-aims-to-reduce-sme-ipo-timeline-launches-ai-tool-to-pre-check-drhps-13152036.html

Economic Times report on BSE's SME IPO AI document-checking initiative:

https://economictimes.indiatimes.com/markets/stocks/news/bse-introduces-ai-tool-to-speed-up-sme-ipo-document-checks/articleshow/121976663.cms

------------------------------------------------------------------------

## 75. Important product caveat

This document defines the intended product and engineering direction. It
is not legal advice, a statement that any particular regulatory control
is legally sufficient, or evidence that every proposed workflow is
currently demanded by every merchant banker.

The product team must continuously distinguish:

1.  **implemented behavior**;
2.  **intended architecture**;
3.  **regulatory requirement**;
4.  **customer-validated pain**;
5.  **engineering hypothesis**.

Regulatory logic must be validated against authoritative current sources
and, where appropriate, qualified Indian capital-markets counsel.

Customer discovery must validate which workflows are sufficiently
painful to justify adoption.

The objective is not to build the largest IPO software platform.

The objective is to build the smallest system that becomes genuinely
difficult for a transaction team to work without because it makes
diligence evidence, transaction truth, disclosure dependencies, and
change impact materially easier to control.
