# Syndicate --- Public Market Transaction Infrastructure

## 1. Product Definition

**Syndicate** is infrastructure for taking a private company through a
public-market transaction.

The first transaction supported is an **Indian SME IPO**.

Syndicate is not primarily a document generator, project-management
application, chat application, or generic AI workspace.

Its fundamental purpose is:

> **Maintain a continuously verified representation of the company and
> the transaction, coordinate the people responsible for establishing
> that truth, preserve the evidence behind every material assertion, and
> produce the regulatory and transaction outputs from that verified
> state.**

The product begins with the company as it exists in reality.

It then creates the structured state required to answer:

-   Who is the company?
-   Who owns it?
-   What does it do?
-   What assets and liabilities does it have?
-   What contracts and obligations exist?
-   What regulators govern it?
-   What financial history can be established?
-   What litigation, related parties, licenses, employees, intellectual
    property, and other material facts exist?
-   Which facts have evidence?
-   Which facts have been independently reviewed?
-   Which facts conflict?
-   Which requirements remain unsatisfied?
-   Who is responsible for resolving each gap?
-   What has changed since the last review?
-   Which disclosures are affected by that change?
-   Is the company actually ready for the transaction?

The final document is therefore an **output of verified state**, not the
primary source of truth.

------------------------------------------------------------------------

# 2. First Principles

## 2.1 What is an IPO fundamentally?

An IPO is not fundamentally a PDF.

An IPO is a controlled transformation:

``` text
Private Company
      ↓
Fact Collection
      ↓
Due Diligence
      ↓
Verification
      ↓
Risk Identification
      ↓
Regulatory Assessment
      ↓
Disclosure
      ↓
Review
      ↓
Approval
      ↓
Public Offering
      ↓
Listed Company
```

The prospectus is a formal representation of the resulting state.

Therefore:

> **The hard problem is establishing trustworthy company state before
> representing it publicly.**

Syndicate is built around that problem.

------------------------------------------------------------------------

## 2.2 Why documents are the wrong system of record

A traditional process often looks like:

``` text
Email
  ↓
Excel
  ↓
PDF
  ↓
Word
  ↓
WhatsApp
  ↓
Reviewer comments
  ↓
Another Excel
  ↓
Another PDF
  ↓
Final document
```

This creates a fundamental information problem.

The same fact may exist in several places:

``` text
Revenue = ₹42 crore
```

but one file says:

``` text
₹42 crore
```

another says:

``` text
₹41.8 crore
```

and another says:

``` text
₹42.3 crore
```

The problem is not document formatting.

The problem is:

> **Which representation of reality is authoritative, and what evidence
> proves it?**

Syndicate treats the fact itself as the primary object.

Documents become projections of that state.

------------------------------------------------------------------------

# 3. Core Product Principle

## The Company Is the Primary Object

Syndicate must not begin with:

``` text
Upload DRHP
```

It begins with:

``` text
Create / identify company
```

The company exists independently of any particular IPO.

A company may:

-   prepare for an IPO;
-   change merchant bankers;
-   postpone an IPO;
-   conduct another transaction;
-   become listed;
-   continue filing disclosures after listing.

Therefore:

``` text
Organization
      ↓
Company Identity
      ↓
Transaction
```

not:

``` text
IPO
  ↓
Company
```

The IPO is a transaction involving an existing company.

------------------------------------------------------------------------

# 4. Core Domain Model

The system should be built around a small number of durable domain
objects.

``` text
User
Organization
OrganizationMembership

Company
CompanyRelationship

Transaction
TransactionMembership

Workstream
Task

Fact
Evidence
Claim

Requirement
Control
Issue

Review
Approval

Disclosure
DisclosureSource

Document
DocumentVersion

Change
AuditEvent
```

The model must remain understandable without AI.

AI operates on top of the domain model.

AI must never become the domain model.

------------------------------------------------------------------------

# 5. Organization Model

The transaction contains multiple professional organizations.

Examples:

``` text
Issuer Company
Merchant Banker
Legal Counsel
Statutory Auditor
CA / Tax Advisor
Company Secretary
Industry / Regulatory Consultant
Registrar
Market Maker
Other Advisors
```

An organization owns its users.

Therefore:

``` text
Organization
    ├── Users
    ├── Roles
    ├── Permissions
    └── External Relationships
```

A user should not simply be granted arbitrary access to an IPO.

Access should be derived from:

``` text
User
+ Organization
+ Transaction Membership
+ Role
+ Workstream
+ Permission
```

------------------------------------------------------------------------

# 6. Company Identity

Company Identity is the persistent representation of the issuer.

It contains structured information such as:

``` text
Legal Name
CIN
PAN
Registered Office
Incorporation Date
Constitution
Capital Structure
Promoters
Directors
Shareholders
Subsidiaries
Business Activities
Locations
Licenses
Financial Periods
Material Contracts
Litigation
Regulatory Relationships
Intellectual Property
Employees
Related Parties
```

This information should not be duplicated independently inside every
transaction.

Instead:

``` text
Company
   │
   ├── Current State
   │
   ├── Historical State
   │
   └── Transactions
```

A transaction can snapshot relevant state at particular points in time
while retaining links to the underlying facts and evidence.

------------------------------------------------------------------------

# 7. Transaction

A transaction represents a specific capital-markets process.

For the initial product:

``` text
Transaction Type = SME IPO
```

Later:

``` text
Main-board IPO
Follow-on Offering
Rights Issue
Preferential Issue
Other Public-Market Transactions
```

A transaction contains:

``` text
Issuer
Lead Merchant Banker
Transaction Participants
Issue Structure
Transaction Timeline
Requirements
Workstreams
Evidence
Issues
Reviews
Approvals
Disclosures
Documents
Audit History
```

------------------------------------------------------------------------

# 8. Two Entry Points

The system must support both real-world initiation patterns.

## Company-first

``` text
Company
  ↓
Start Transaction
  ↓
Select Transaction Type
  ↓
Readiness Assessment
  ↓
Invite / appoint Merchant Banker
  ↓
Transaction Activated
```

## Merchant-banker-first

``` text
Merchant Banker
  ↓
Create Transaction
  ↓
Identify Issuer
  ↓
Define Transaction
  ↓
Invite Issuer
  ↓
Assemble Advisors
  ↓
Transaction Activated
```

These are two entry paths into the same domain object.

They must not create two different representations of the company.

------------------------------------------------------------------------

# 9. Transaction Membership

A transaction is a controlled collaboration boundary.

Example:

``` text
Transaction
│
├── Issuer
│   ├── Promoter
│   ├── CFO
│   └── Company Secretary
│
├── Merchant Banker
│   ├── Lead Manager
│   ├── Due Diligence Team
│   └── Transaction Team
│
├── Legal Counsel
│   ├── Lead Lawyer
│   └── Associates
│
├── Auditor
│
└── Other Advisors
```

Organizations should be invited first.

The organization then decides which of its members participate.

This prevents the transaction owner from manually managing every
individual external user.

------------------------------------------------------------------------

# 10. Access Control

Authorization must be explicit.

A useful mental model is:

``` text
Can User X perform Action Y
on Object Z
inside Transaction T
because of Role R?
```

Example:

``` text
CFO
→ edit financial facts
→ upload financial evidence
→ respond to financial issues
→ review financial disclosures

Lawyer
→ manage legal facts
→ review contracts
→ manage litigation
→ review legal disclosures

Merchant Banker
→ orchestrate transaction
→ assign work
→ review readiness
→ manage transaction-level approvals
```

Permissions must be scoped.

Avoid:

``` text
Admin = can do everything
```

as the primary security model.

------------------------------------------------------------------------

# 11. Workstreams

The transaction should be decomposed by professional responsibility.

Possible workstreams:

``` text
Transaction Readiness
Corporate / Secretarial
Financial Due Diligence
Legal Due Diligence
Tax
Business Due Diligence
Regulatory Due Diligence
Promoter / Shareholding
Capital Structure
Material Contracts
Litigation
Intellectual Property
Human Resources
Related Parties
Risk Factors
Financial Information
Offer / Issue Structure
DRHP / Prospectus
Exchange Queries
Issue Preparation
Listing
Post-Listing
```

Each workstream contains:

``` text
Requirements
Tasks
Facts
Evidence
Issues
Reviews
Approvals
Disclosures
```

This is important because IPO preparation is not one large task.

It is a collection of professional investigations whose results must
converge into one consistent company representation.

------------------------------------------------------------------------

# 12. The Evidence Model

The central data structure should be evidence-driven.

A statement without evidence should be distinguishable from a verified
statement.

Example:

``` text
Fact:
The company owns manufacturing facility X.

Evidence:
Registered property document
```

Another:

``` text
Fact:
Manufacturing licence is valid until 31 March 2028.

Evidence:
Drug manufacturing licence PDF
```

The system should preserve:

``` text
Fact
  ↓
Evidence
  ↓
Source
  ↓
Reviewer
  ↓
Review Decision
  ↓
Approval
```

Every important assertion should answer:

> **Where did this come from?**

------------------------------------------------------------------------

# 13. Facts

A Fact represents a structured assertion about reality.

Example:

``` text
Revenue
Company: ABC Pharma Pvt Ltd
Period: FY2026
Value: ₹42,18,00,000
Currency: INR
Source: Audited Financial Statements
Status: Verified
Verified By: Auditor
Verified At: ...
```

Facts should support:

-   value;
-   unit;
-   period;
-   effective date;
-   source;
-   confidence;
-   verification status;
-   owner;
-   reviewer;
-   version;
-   supersession.

Facts must be immutable in history.

If revenue changes:

``` text
Fact v1 → Fact v2
```

rather than silently overwriting history.

------------------------------------------------------------------------

# 14. Claims

A Claim is a statement that may be composed from one or more facts.

Example:

``` text
Claim:
The company operates a WHO-GMP compliant manufacturing facility.
```

Supporting facts may include:

``` text
Manufacturing facility exists
+
Relevant licence exists
+
GMP certification exists
+
Certificate is valid
```

Claims therefore provide the bridge between structured company state and
narrative disclosure.

------------------------------------------------------------------------

# 15. Evidence

Evidence can originate from:

``` text
PDF
Excel
Word
Scanned document
Image
Email
Structured data
Government filing
Audited statement
Certificate
Contract
Board resolution
Licence
External record
```

The system should preserve:

``` text
Original file
Hash
Source
Upload time
Uploader
Document type
Relevant pages
Extracted text
Extracted fields
Related facts
Related claims
Review history
```

Never discard the original evidence.

Derived information is not a substitute for source material.

------------------------------------------------------------------------

# 16. Document Intelligence

AI is primarily useful for converting unstructured evidence into
structured candidates.

Pipeline:

``` text
Document
   ↓
Classification
   ↓
Parsing / OCR
   ↓
Section Detection
   ↓
Entity Extraction
   ↓
Fact Extraction
   ↓
Claim Extraction
   ↓
Evidence Linking
   ↓
Validation
   ↓
Human Review
   ↓
Accepted Fact
```

The output of AI should initially be:

``` text
Candidate Fact
```

not:

``` text
Truth
```

A human or deterministic validation process promotes the candidate into
trusted state.

------------------------------------------------------------------------

# 17. Never Let AI Become the Source of Truth

The system must distinguish:

``` text
AI inferred
AI extracted
AI suggested
Human verified
System verified
Externally verified
Approved
```

For example:

``` text
AI:
Revenue = ₹42 crore

Evidence:
Audited financial statement

Auditor:
Confirmed

System:
Verified
```

This is fundamentally different from:

``` text
LLM:
Revenue = ₹42 crore
```

The latter is an untraceable assertion.

------------------------------------------------------------------------

# 18. Conflict Detection

One of the highest-value functions is detecting contradictory
representations.

Example:

``` text
Source A:
Promoter holding = 51.2%

Source B:
Promoter holding = 50.8%

Source C:
Promoter holding = 51.0%
```

Syndicate should create:

``` text
Issue:
Conflicting promoter holding figures
```

with:

``` text
Affected Facts
Source Documents
Detected Values
Periods
Potential Impact
Responsible Workstream
Assigned Owner
Resolution
Resolution Evidence
Final Approved Value
```

The system should not silently select one.

------------------------------------------------------------------------

# 19. Requirement Engine

Regulatory readiness should be represented as structured requirements.

Conceptually:

``` text
Requirement
    ↓
Condition
    ↓
Evidence Required
    ↓
Validation
    ↓
Status
```

Example:

``` text
Requirement:
Minimum operating-profit condition

Inputs:
Historical financial facts

Validation:
Deterministic rule

Result:
PASS / FAIL / INSUFFICIENT DATA
```

Another:

``` text
Requirement:
Valid manufacturing licence

Inputs:
Licence facts
Expiry date
Facility
Product scope

Validation:
Licence present
+
valid
+
applicable

Result:
PASS / FAIL / REVIEW
```

Rules should be versioned.

A rule is not timeless.

Therefore:

``` text
Rule Version
Effective Date
Jurisdiction
Regulatory Source
Applicability
```

must be retained.

------------------------------------------------------------------------

# 20. Compliance Is a State, Not a Checkbox

Avoid:

``` text
✓ Compliant
```

without context.

Instead:

``` text
Requirement
→ Applicable?
→ Evidence available?
→ Evidence current?
→ Rule satisfied?
→ Human review required?
→ Approved?
```

Possible states:

``` text
NOT_ASSESSED
NOT_APPLICABLE
MISSING
INSUFFICIENT
FAILED
REQUIRES_REVIEW
PASSED
APPROVED
SUPERSEDED
```

------------------------------------------------------------------------

# 21. Readiness

Readiness is derived from the state of the transaction.

It should not be a manually entered percentage.

Bad:

``` text
IPO Readiness = 78%
```

Better:

``` text
Critical requirements:
48
Passed:
41
Failed:
2
Missing evidence:
3
Under review:
2
```

Then:

``` text
Readiness Status:
NOT READY
CONDITIONALLY READY
READY FOR REVIEW
READY FOR FILING
```

The system should explain why.

------------------------------------------------------------------------

# 22. Issues

Every unresolved problem becomes an explicit object.

``` text
Issue
├── Severity
├── Category
├── Workstream
├── Owner
├── Affected Facts
├── Affected Documents
├── Regulatory Impact
├── Due Date
├── Status
├── Resolution
└── Resolution Evidence
```

Example:

``` text
Issue:
Licence expires before expected filing period.

Severity:
HIGH

Owner:
Regulatory Advisor

Required Action:
Renew licence

Affected:
Regulatory readiness
Business description
Risk factors
DRHP disclosures
```

------------------------------------------------------------------------

# 23. Change Propagation

This is a fundamental capability.

Suppose:

``` text
Promoter holding changes
51% → 48%
```

The system must identify dependent objects.

``` text
Fact changes
   ↓
Capital Structure
   ↓
Shareholding Tables
   ↓
Promoter Disclosure
   ↓
Risk Factors
   ↓
Issue Structure
   ↓
DRHP Sections
   ↓
Derived Reports
```

A user should not manually search every document for the old number.

The system should produce:

``` text
Impact detected:
17 dependent disclosures
4 tables
2 review items
1 approval invalidated
```

This is one of the core reasons to model the underlying state.

------------------------------------------------------------------------

# 24. Disclosure Graph

The relationship between facts and disclosures should be explicit.

``` text
Evidence
   ↓
Fact
   ↓
Claim
   ↓
Disclosure
   ↓
Document Version
```

A disclosure should therefore be traceable.

Example:

``` text
DRHP § X:
"Revenue from operations increased..."

Sources:
Audited Financial Statements
Fact IDs:
F-104
F-105
F-106

Review:
Financial DD Review #27

Approval:
CFO + Merchant Banker
```

The exact implementation can evolve, but the principle must remain.

------------------------------------------------------------------------

# 25. Document Generation

The document generator should consume verified state.

``` text
Verified Company State
        +
Transaction Configuration
        +
Disclosure Rules
        ↓
Document Compiler
        ↓
Draft
        ↓
Human Review
        ↓
Approved Version
```

This is fundamentally different from asking an LLM:

``` text
"Write a DRHP for this company."
```

The LLM may assist with prose, but the source data must come from the
structured system.

------------------------------------------------------------------------

# 26. Document Compiler

Treat document generation more like compilation than writing.

``` text
Source Model
      ↓
Disclosure Model
      ↓
Section Model
      ↓
Tables
      ↓
Narrative
      ↓
Validation
      ↓
Document
```

The compiler should detect:

``` text
Missing required data
Conflicting values
Stale facts
Unapproved disclosures
Unresolved high-severity issues
Broken evidence references
```

before producing a filing candidate.

------------------------------------------------------------------------

# 27. Versioning

Everything material should be versioned.

At minimum:

``` text
Company State
Facts
Evidence
Rules
Requirements
Issues
Disclosures
Documents
Approvals
Access
```

A document version must be reproducible from the state that generated
it.

The system should be able to answer:

> Why did this sentence appear in version 7?

And:

> What changed between version 6 and version 7?

------------------------------------------------------------------------

# 28. Approval Model

Approval should be explicit.

Possible model:

``` text
Draft
   ↓
Reviewed
   ↓
Corrections Required
   ↓
Approved
   ↓
Superseded
```

Approval should record:

``` text
Actor
Organization
Role
Timestamp
Object
Version
Decision
Comment
```

An approval should attach to a particular version/state.

Changing a material underlying fact can invalidate downstream approval.

------------------------------------------------------------------------

# 29. Collaboration

Do not build generic chat as the center of the product.

The unit of collaboration is:

``` text
Object + Issue + Evidence + Decision
```

Instead of:

``` text
"Can someone check this?"
```

use:

``` text
Issue #184

Question:
Confirm whether Agreement X remains active.

Assigned:
Legal Team

Evidence requested:
Current agreement + amendments

Status:
Awaiting issuer
```

Communication should attach to work.

------------------------------------------------------------------------

# 30. Task System

Tasks exist because something must happen to change transaction state.

A task should have:

``` text
Owner
Workstream
Input
Expected Output
Dependency
Due Date
Status
Evidence
Resolution
```

Avoid creating thousands of meaningless project-management tasks.

The system should generate tasks from:

``` text
Requirements
Missing evidence
Detected conflicts
Review findings
Regulatory changes
Document dependencies
```

------------------------------------------------------------------------

# 31. Transaction State Machine

A transaction should have an explicit lifecycle.

Example:

``` text
DRAFT
  ↓
INITIATED
  ↓
PARTICIPANTS_ASSEMBLING
  ↓
DUE_DILIGENCE
  ↓
READINESS_REVIEW
  ↓
DOCUMENT_PREPARATION
  ↓
INTERNAL_APPROVAL
  ↓
FILING_READY
  ↓
FILED
  ↓
EXCHANGE_REVIEW
  ↓
QUERIES
  ↓
ISSUE_PREPARATION
  ↓
LISTED
```

Actual regulatory states must be configurable rather than hard-coded
into every service.

------------------------------------------------------------------------

# 32. Pharma SME Example

Consider an SME pharmaceutical manufacturer in Rajasthan.

The company may have:

``` text
Manufacturing Facilities
Drug Licences
Products
GMP / Schedule M obligations
Environmental permissions
Employees
Technical Personnel
Analytical Laboratories
Quality Systems
Customers
Distributors
Material Contracts
Financial Statements
Promoters
Loans
Related Parties
Litigation
Intellectual Property
```

The platform should not simply upload all these documents into a folder.

It should transform them into structured state.

Example:

``` text
Manufacturing Licence
        ↓
Licence Entity
        ↓
Facility
        ↓
Products Covered
        ↓
Expiry
        ↓
Regulatory Requirements
        ↓
Evidence
        ↓
Verification
```

A licence expiry can then become a transaction issue automatically.

------------------------------------------------------------------------

# 33. Knowledge Graph

A graph is useful because company information is relational.

Example:

``` text
Company
 ├── owns → Facility
 │             ├── has → Licence
 │             └── manufactures → Product
 │
 ├── employs → Director
 │             └── owns → Shares
 │
 ├── contracts_with → Customer
 │
 ├── owes → Loan
 │
 └── related_to → Promoter Entity
```

The graph is not the product by itself.

It is an implementation mechanism for navigating relationships and
dependencies.

Use it where relationships provide actual value.

Do not introduce graph infrastructure merely because "knowledge graph"
sounds advanced.

------------------------------------------------------------------------

# 34. Search

Users should be able to ask:

``` text
Show all material contracts.
```

``` text
Which licences expire within 12 months?
```

``` text
Which disclosures depend on FY2026 revenue?
```

``` text
Show all unresolved legal issues.
```

``` text
Why is the company not ready?
```

``` text
Which evidence supports this disclosure?
```

The answer should come from the structured transaction state and
evidence graph.

------------------------------------------------------------------------

# 35. AI Architecture

AI should be used where probabilistic computation is useful.

Good uses:

``` text
Document classification
OCR correction
Entity extraction
Fact extraction
Semantic matching
Duplicate detection
Conflict detection
Contract summarization
Issue discovery
Question generation
Disclosure drafting
Natural-language search
```

Deterministic systems should handle:

``` text
Permissions
Workflow state
Rule evaluation
Calculations
Versioning
Audit logging
Approval state
Evidence integrity
Financial arithmetic
Regulatory thresholds
```

Human experts should handle:

``` text
Material legal interpretation
Regulatory judgment
Risk acceptance
Final factual verification
Disclosure approval
Transaction decisions
```

------------------------------------------------------------------------

# 36. AI Agent Model

Agents should be narrow and accountable.

Potential agents:

``` text
Document Agent
Financial Agent
Legal Agent
Regulatory Agent
Disclosure Agent
Consistency Agent
Readiness Agent
Research Agent
```

Each agent operates against explicit tools and domain boundaries.

An agent should produce:

``` text
Observation
Evidence
Proposed Action
Confidence
Reason
Affected Objects
```

not silently mutate critical company state.

------------------------------------------------------------------------

# 37. Agent Safety Boundary

An agent must not be able to:

``` text
change a verified financial fact
approve a disclosure
declare legal compliance
grant itself permissions
delete evidence
alter audit history
```

without an authorized deterministic workflow or human action.

The system should be designed so that a model failure cannot silently
corrupt the transaction record.

------------------------------------------------------------------------

# 38. Auditability

Every important mutation should create an audit event.

Example:

``` text
User A
changed Fact F-102
from:
₹41.8 crore

to:
₹42.18 crore

Source:
Audited FY2026 statement

Reason:
Correction

Timestamp:
...
```

Likewise:

``` text
User B
approved Disclosure D-44
```

The audit trail should be append-only from the application perspective.

------------------------------------------------------------------------

# 39. Security

The product contains extremely sensitive corporate information.

Security must therefore be a foundational property.

Required concepts include:

``` text
Tenant isolation
Organization isolation
Transaction-level authorization
Encryption in transit
Encryption at rest
Secret management
Audit logs
Least privilege
Session management
MFA support
Document access controls
Secure file handling
Retention policies
Backup / recovery
```

Do not design security as a feature added after MVP.

------------------------------------------------------------------------

# 40. Multi-Tenancy

The natural tenancy boundary is the organization.

However, sensitive data also requires transaction-level isolation.

Conceptually:

``` text
Tenant
  ↓
Organization
  ↓
Transaction
  ↓
Workstream
  ↓
Object
```

A legal firm participating in Transaction A must not automatically see
Transaction B.

An issuer participating in one transaction must not gain access to
another issuer.

------------------------------------------------------------------------

# 41. External Participants

External access should be deliberate.

Invitation:

``` text
Merchant Banker
     ↓
Invite Legal Firm
     ↓
Legal Firm Accepts
     ↓
Legal Firm Selects Members
     ↓
Members Receive Transaction Access
```

Offboarding:

``` text
Organization Removed
      ↓
Access Revoked
      ↓
Historical Contributions Preserved
      ↓
Audit Event Created
```

Never delete historical work merely because a participant leaves.

------------------------------------------------------------------------

# 42. Regulatory Knowledge

Regulation must be treated as data.

Do not hard-code regulatory logic throughout the application.

Represent:

``` text
Regulation
Provision
Requirement
Rule
Applicability
Effective Date
Source
Version
Jurisdiction
Transaction Type
```

Example:

``` text
Rule R-102
Applicable:
SME IPO

Effective:
Date A

Condition:
...

Source:
Regulatory provision X
```

When regulation changes:

``` text
Old Rule
    ↓
Superseded
    ↓
New Rule
    ↓
Affected Transactions
    ↓
Reassessment
```

------------------------------------------------------------------------

# 43. Current-State vs Historical-State

The company has a present state and a history.

Example:

``` text
Promoter Holding

2025:
55%

2026:
51%

Pre-Issue:
48%
```

The system must understand time.

Every important fact should be evaluated with temporal context:

``` text
valid_from
valid_to
as_of_date
period
effective_date
```

Without temporal modeling, historical IPO documents become difficult to
reproduce correctly.

------------------------------------------------------------------------

# 44. Source Authority

Not every source has equal authority.

The system should support source hierarchy.

For example:

``` text
Audited Financial Statement
        >
Management Spreadsheet
        >
AI Extraction
```

The exact hierarchy depends on the fact.

A regulatory licence may outrank an internal spreadsheet.

A board-approved resolution may outrank an informal email.

Authority should therefore be configurable by fact type.

------------------------------------------------------------------------

# 45. Data Quality

The platform should continuously calculate data-quality conditions.

Examples:

``` text
Missing
Stale
Contradictory
Unverified
Unsupported
Out of date
Low-confidence extraction
Missing source
Missing reviewer
```

A company should not be considered ready simply because most fields are
filled.

------------------------------------------------------------------------

# 46. The Readiness Graph

Readiness can be represented as dependencies.

Example:

``` text
Filing Ready
   │
   ├── Corporate Ready
   │
   ├── Financial Ready
   │
   ├── Legal Ready
   │
   ├── Regulatory Ready
   │
   ├── Disclosure Ready
   │
   └── Approval Ready
```

Each branch contains requirements.

The system can therefore explain:

``` text
Not ready because:

2 critical legal issues remain
1 licence lacks valid evidence
3 financial facts conflict
4 disclosures await approval
```

This is more useful than a dashboard full of arbitrary percentages.

------------------------------------------------------------------------

# 47. The Transaction Timeline

A timeline should be generated from actual state changes.

Example:

``` text
09:42 — Financial statements uploaded
10:05 — AI extracted 184 candidate facts
10:18 — Auditor verified 162 facts
11:07 — 4 conflicts detected
11:20 — CFO assigned conflict resolution
14:12 — Conflicts resolved
15:30 — Financial workstream approved
```

The timeline becomes an operational history of the transaction.

------------------------------------------------------------------------

# 48. Notifications

Notifications should represent meaningful state changes.

Examples:

``` text
Evidence requested
Requirement failed
Issue assigned
Approval requested
Evidence expired
Regulation changed
Disclosure invalidated
Participant access changed
Exchange query received
```

Avoid notification noise.

------------------------------------------------------------------------

# 49. External Regulatory / Market Data

Where public data is required, Syndicate can integrate external sources.

Potential sources:

``` text
Regulatory databases
Stock exchanges
Corporate registries
Government portals
Market data
Industry databases
```

External data should be stored with:

``` text
Source
Timestamp
Retrieval method
Version / snapshot
Confidence
```

Never represent external retrieval as permanent truth without recording
when it was obtained.

------------------------------------------------------------------------

# 50. Architecture Principle

The architecture should follow domain boundaries rather than technology
fashion.

Initial logical architecture:

``` text
                    ┌─────────────────────┐
                    │      Web Client     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │     API Boundary    │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
 Company Domain        Transaction Domain       Identity / Access
        │                      │                      │
        └──────────────┬───────┴──────────────┬───────┘
                       │                      │
                       ▼                      ▼
                 Evidence Domain       Workflow / Rules
                       │                      │
                       └──────────┬───────────┘
                                  ▼
                           Disclosure Domain
                                  │
                                  ▼
                         Document Compilation
```

This is a logical architecture, not a requirement to create dozens of
microservices.

------------------------------------------------------------------------

# 51. Start as a Modular Monolith

The initial implementation should prefer a modular monolith.

Reason:

The dominant early problem is domain discovery, not independent service
scaling.

A sensible boundary is:

``` text
identity
organizations
companies
transactions
workstreams
evidence
facts
requirements
issues
reviews
disclosures
documents
audit
```

Keep module boundaries clean.

Extract services only when operational evidence justifies it.

------------------------------------------------------------------------

# 52. Database

A relational database should be the primary system of record.

Core entities are relational:

``` text
Organizations
Users
Companies
Transactions
Memberships
Facts
Evidence
Issues
Requirements
Reviews
Approvals
Documents
Audit Events
```

A graph database can be introduced later for relationship-heavy
workloads.

Object storage should hold original documents.

Search infrastructure should index derived text and metadata.

------------------------------------------------------------------------

# 53. Event Model

Important state changes should generate domain events.

Examples:

``` text
FactVerified
FactSuperseded
EvidenceUploaded
EvidenceExpired
IssueCreated
IssueResolved
RequirementFailed
RequirementPassed
ReviewCompleted
ApprovalGranted
ApprovalInvalidated
DisclosureChanged
DocumentGenerated
ParticipantAdded
ParticipantRemoved
```

Events allow downstream systems to react without tightly coupling every
module.

------------------------------------------------------------------------

# 54. Idempotency and Reliability

Transaction infrastructure must assume:

``` text
network failures
duplicate requests
retries
partial uploads
worker failures
LLM timeouts
external API failures
concurrent edits
```

Operations should therefore be:

``` text
idempotent
retryable
observable
auditable
recoverable
```

Never assume a background AI job will run exactly once.

------------------------------------------------------------------------

# 55. Concurrency

Multiple professionals will work simultaneously.

Example:

``` text
CFO updates financial fact
while
Auditor reviews previous version
while
Lawyer prepares disclosure
```

The system needs explicit versioning and conflict semantics.

Do not solve concurrency by simply overwriting the latest value.

------------------------------------------------------------------------

# 56. Search and Retrieval

Search should operate over:

``` text
Documents
Facts
Claims
Issues
Requirements
Organizations
People
Contracts
Licences
Disclosures
Audit events
```

Semantic search can assist discovery.

Structured filters should remain available.

A user should be able to combine:

``` text
"manufacturing licence"
+
Facility = Jaipur
+
Status = Expiring
```

------------------------------------------------------------------------

# 57. MVP

The MVP should not attempt to automate the entire IPO.

The first useful system should solve one narrow but fundamental loop:

``` text
Company
  ↓
Transaction
  ↓
Participants
  ↓
Evidence Collection
  ↓
Fact Extraction
  ↓
Verification
  ↓
Issues
  ↓
Readiness
  ↓
Traceable Disclosure
```

Initial capabilities:

``` text
Authentication
Organizations
Company Identity
Transaction Creation
Participant Invitations
Workstreams
Document Upload
Document Classification
Fact Extraction
Evidence Linking
Fact Review
Conflict Detection
Requirements
Issues
Readiness
Audit Trail
Basic Disclosure Generation
```

------------------------------------------------------------------------

# 58. What Not to Build First

Do not begin with:

``` text
Generic chat
Huge dashboard
Complex AI agents
Autonomous legal advice
Full DRHP generation
Microservices
Blockchain
Custom graph database
Complex workflow designer
Mobile application
Marketplace
```

These can distract from the core problem.

The first question should always be:

> **Can Syndicate establish and maintain a trustworthy transaction state
> better than spreadsheets, email, PDFs, and disconnected professional
> workflows?**

If not, additional features are irrelevant.

------------------------------------------------------------------------

# 59. Product Flywheel

The system improves as transactions accumulate.

``` text
More Transactions
      ↓
More Structured Facts
      ↓
More Document Patterns
      ↓
More Requirement Patterns
      ↓
Better Extraction
      ↓
Better Conflict Detection
      ↓
Better Readiness Analysis
      ↓
Faster Transactions
      ↓
More Transactions
```

The defensibility comes from structured transaction knowledge and
workflow integration, not merely from access to an LLM.

------------------------------------------------------------------------

# 60. The Real Moat

The moat should become:

``` text
Verified Company Data
+
Evidence Graph
+
Transaction History
+
Regulatory Rule Model
+
Disclosure Dependencies
+
Professional Workflow
+
Audit Trail
```

A generic AI company can generate prose.

It is substantially harder to reproduce:

``` text
Why this fact is trusted
Who verified it
What evidence supports it
Which requirements depend on it
Which disclosures depend on it
What changed
Who approved it
What becomes invalid after the change
```

That is the infrastructure layer.

------------------------------------------------------------------------

# 61. Business Model

Initial customers:

``` text
Merchant Bankers
IPO Advisors
SME Issuers
Professional Advisory Firms
```

The merchant banker is a particularly important customer because it
coordinates the transaction and repeatedly manages issuers.

Potential commercial model:

``` text
Transaction Fee
+
Platform Subscription
+
Organization Seats
+
Document / Processing Usage
+
Enterprise Contracts
```

Pricing should eventually follow transaction value and operational
savings rather than raw document volume alone.

------------------------------------------------------------------------

# 62. Expansion

Do not architect the company around one document.

Start:

``` text
SME IPO
```

Then expand into:

``` text
Public-market transactions
Continuous compliance
Annual reports
Corporate governance
Investor disclosures
Board reporting
Regulatory monitoring
Post-listing obligations
Capital-market data infrastructure
```

The company identity and evidence model make this expansion possible.

------------------------------------------------------------------------

# 63. Long-Term Product Model

The ultimate system should look like:

``` text
                    COMPANY
                       │
        ┌──────────────┼──────────────┐
        │              │              │
      FACTS         EVIDENCE       RELATIONSHIPS
        │              │              │
        └──────────────┼──────────────┘
                       │
                 VERIFIED STATE
                       │
              ┌────────┴────────┐
              │                 │
          REQUIREMENTS       TRANSACTIONS
              │                 │
              │          ┌──────┴──────┐
              │          │             │
           READINESS   PARTICIPANTS  WORKSTREAMS
              │          │             │
              └──────────┼─────────────┘
                         │
                    DISCLOSURES
                         │
                    DOCUMENTS
                         │
                    PUBLIC MARKET
```

The document is at the end.

The verified company state is at the center.

------------------------------------------------------------------------

# 64. Fundamental Invariants

These principles should survive every future architectural change.

### Invariant 1

**The company exists independently of a transaction.**

### Invariant 2

**A transaction is a controlled state transition involving multiple
organizations.**

### Invariant 3

**Evidence precedes trust.**

### Invariant 4

**AI suggestions are not authoritative facts.**

### Invariant 5

**Critical decisions require explicit human or deterministic
validation.**

### Invariant 6

**Material state changes must be versioned.**

### Invariant 7

**Every material disclosure must be traceable to underlying facts and
evidence.**

### Invariant 8

**Rules must be versioned by effective date and source.**

### Invariant 9

**Access must be scoped to organization, transaction, role, and
object.**

### Invariant 10

**Historical state must remain reproducible.**

### Invariant 11

**Documents are derived artifacts, not the system of record.**

### Invariant 12

**No component should be able to silently corrupt verified state.**

------------------------------------------------------------------------

# 65. The Core Loop

Everything in Syndicate should ultimately reinforce this loop:

``` text
COLLECT
   ↓
STRUCTURE
   ↓
LINK TO EVIDENCE
   ↓
VERIFY
   ↓
DETECT GAPS / CONFLICTS
   ↓
RESOLVE
   ↓
REVIEW
   ↓
APPROVE
   ↓
DISCLOSE
   ↓
GENERATE OUTPUT
   ↓
MONITOR CHANGE
   ↓
REASSESS
```

This loop is the product.

The UI, AI agents, database, workflows, document compiler, and
integrations are implementation mechanisms around it.

------------------------------------------------------------------------

# 66. Final Product Thesis

Syndicate should be understood as:

> **A system for establishing, verifying, coordinating, and maintaining
> the truth about a company during a public-market transaction.**

The immediate market is SME IPO preparation.

The deeper problem is larger:

> **Capital markets still depend heavily on fragmented information,
> human coordination, documents, spreadsheets, email, and repeated
> manual reconciliation.**

Syndicate attacks the information problem underneath those workflows.

The desired end state is not:

``` text
"AI writes IPO documents faster."
```

It is:

``` text
Company Reality
      ↓
Structured Company State
      ↓
Evidence
      ↓
Verification
      ↓
Regulatory / Transaction Requirements
      ↓
Human Decisions
      ↓
Approved Disclosures
      ↓
Regulatory Documents
```

That distinction defines the architecture.

**Syndicate is infrastructure for turning company reality into trusted
public-market information.**
