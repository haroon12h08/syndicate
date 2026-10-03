# Security

Syndicate holds confidential documents about companies preparing to go public — draft financials,
bank statements, litigation papers, board resolutions. Treat anything in a transaction as material
non-public information.

## Reporting a vulnerability

Please report privately, not in a public issue:

- open a [private security advisory](../../security/advisories/new) on this repository, or
- email the maintainers with `SECURITY` in the subject.

Include what you found, how to reproduce it, and what an attacker could reach. We will acknowledge
within a few working days and keep you updated while it is being fixed. Please give us a reasonable
window before disclosing publicly.

## What is implemented

- Organisation and transaction isolation enforced server-side on every protected resource, with a
  test that walks every endpoint as an outsider.
- Role-based permissions with a parameterised test over all twelve transaction roles.
- 15-minute access tokens kept only in page memory; sessions carried by an `HttpOnly`,
  `SameSite=Strict` cookie scoped to the auth routes, stored server-side only as a hash, rotated on
  every refresh, with reuse detection that revokes the whole chain.
- Uploads validated by content rather than by filename or declared type, within a size limit.
- Rate limiting on sign-in and sign-up, per source address.
- Content Security Policy, frame denial, referrer policy, HSTS.
- Append-only, hash-chained audit: the database rejects updates and deletes by trigger, and the chain
  can be recomputed through the API.
- Evidence is confidential by default and is archived rather than deleted.

## What is not implemented yet

Do not run a public deployment holding real issuer documents until these exist:

- email verification and account recovery;
- malware scanning of uploads;
- multi-factor authentication for approving roles;
- object storage with versioning, retention and legal hold (evidence currently sits on a volume);
- tested backup and restore;
- metrics, alerting and access logging suitable for a regulated workflow.

## Scope

The system produces a filing-ready package. It does not file anything, and it never asserts that a
regulator has approved an issue. Rules shipped with it are configured checks, not statements of
regulation — see README §8.
