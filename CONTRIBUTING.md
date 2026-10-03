# Contributing

Thank you for looking at this. A few things are worth knowing before you write code, because this
system has rules that are easy to break by accident.

## The invariants come first

The principles in [§2 of the README](README.md#2-design-principles) are the product. A change that
weakens one of them will be refused even if it is convenient, well written and passes CI. In
particular:

- machine output never becomes a fact without a person accepting it;
- a material fact is never verified by whoever recorded it;
- contradictions are never resolved automatically;
- material values are versioned, never overwritten;
- a filing copy never prints an unverified value;
- the audit trail is never updated or deleted.

Each invariant has a test (README §6.2). If your change makes one fail, the change is wrong, not the
test.

## Working on it

```bash
./start.sh                                  # the whole product on :8080
cd backend && ./mvnw spring-boot:run        # API with reload, :8080
cd frontend && npm install && npm run dev   # UI with reload, :5173
```

Before opening a pull request:

```bash
cd backend && ./mvnw verify                 # needs Docker running
cd frontend && npm run lint && npm run build
```

## Conventions

**Database.** Never edit an applied migration; add the next `V<n>__name.sql`. Migrations must be
additive and run against a database at the previous version. Hibernate runs with
`ddl-auto=validate`, so the schema and the entities must agree.

**Service changes.** Every mutation validates authorisation, checks the object's state, writes an
audit record in the same transaction, and returns the resulting version.

**Tests.** Anything touching transaction state needs an integration test driven through the HTTP API,
not a service call — the API is where authorisation and transaction boundaries actually live.

**Comments.** Explain why, not what. If a line of code needs a comment to say what it does, rename
something instead.

**Messages to people.** Error messages name the way forward ("Verifying a financial fact is for an
Auditor or the Issuer Admin"), never the machine's internal state. Nothing in the interface may
imply that a regulator has approved anything.

## Regulatory content

Rules shipped with the product carry a provenance status. Do not add or change a rule, or mark one
`SOURCED`, without a citation to the authoritative text and review by someone qualified to read it.
Engineers do not author regulatory content alone.

## Commits and pull requests

One logical change per pull request, with a message that says what changed and why it matters.
Describe behaviour in terms a transaction team would recognise.
