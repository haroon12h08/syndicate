import { chromium } from 'playwright';
import fs from 'fs';

const B = 'http://localhost:8080';
const API = `${B}/api`;
const OUT = process.argv[2];
const stamp = Date.now();
const marks = [];
const t0 = () => Date.now();
let started = 0;
const mark = (label) => {
  const at = ((Date.now() - started) / 1000);
  marks.push({ at, label });
  console.log(`${at.toFixed(1).padStart(6)}s  ${label}`);
};

const api = async (token, method, path, body) => {
  const res = await fetch(API + path, { method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 200)}`);
  return text ? JSON.parse(text) : null;
};
const register = (name, full, org, type) => api(null, 'POST', '/auth/register', {
  email: `${name}-${stamp}@syndicate.local`, password: 'password123', fullName: full,
  organizationName: org, organizationType: type });

// ---------------------------------------------------------------- seed the deal
console.log('seeding...');
const banker = await register('asha', 'Asha Mehta', 'Meridian Capital', 'MERCHANT_BANKER');
const auditor = await register('ravi', 'Ravi Iyer', 'Iyer & Co', 'STATUTORY_AUDITOR');
const admin = await register('nina', 'Nina Shah', 'Shree Promoters', 'ISSUER');
const tx = await api(banker.token, 'POST', '/transactions/start', { companyName: 'Shree Polymers Limited' });
for (const [u, role] of [[auditor, 'AUDITOR'], [admin, 'ISSUER_ADMIN']]) {
  await api(banker.token, 'POST', `/organizations/${banker.organization.id}/members`,
    { email: u.user.email, role: 'MEMBER' });
  await api(banker.token, 'POST', `/transactions/${tx.id}/memberships`,
    { organizationId: banker.organization.id, email: u.user.email, role });
}
const ws = Object.fromEntries((await api(banker.token, 'GET', `/transactions/${tx.id}/workstreams`))
  .map(w => [w.type, w.id]));

async function upload(workstreamId, name, text, type = 'OTHER') {
  const form = new FormData();
  form.append('file', new Blob([text], { type: 'text/plain' }), name);
  form.append('documentType', type);
  const res = await fetch(`${API}/workstreams/${workstreamId}/evidence`,
    { method: 'POST', headers: { Authorization: 'Bearer ' + banker.token }, body: form });
  return res.json();
}
async function establish(workstreamId, factKey, label, value, period, validFrom) {
  const ev = await upload(workstreamId, `${label.replace(/\s+/g, '-').toLowerCase()}.txt`,
    `${label} ${value} ${Math.random()}`);
  const fact = await api(banker.token, 'POST', `/workstreams/${workstreamId}/facts`,
    { factKey, label, value, unit: 'INR', period, validFrom });
  await api(banker.token, 'POST', `/facts/${fact.id}/evidence-links`, { evidenceId: ev.id });
  await api(banker.token, 'PUT', `/evidence/${ev.id}/quality`, { quality: 'ACCEPTABLE' });
  await api(auditor.token, 'POST', `/facts/${fact.id}/verify`);
  return fact;
}
const capitalFact = await establish(ws.CAPITAL_STRUCTURE, 'issue.post_issue_paid_up_capital',
  'Post-issue paid-up capital', '180000000');
let ebitdaFact = null;
for (const [v, p] of [['64000000', 'FY2026'], ['51000000', 'FY2025'], ['43000000', 'FY2024']]) {
  const f = await establish(ws.FINANCIAL_DUE_DILIGENCE, 'issuer.ebitda', 'EBITDA', v, p);
  if (p === 'FY2026') ebitdaFact = f;
}
await establish(ws.REGULATORY_DUE_DILIGENCE, 'issuer.licence_expiry', 'Factory licence expiry', '4102444800000');

// two sources that disagree about the same period: a real conflict for the demo
const regA = await upload(ws.CAPITAL_STRUCTURE, 'shareholder-register-september.txt', 'Promoter holding 49% ' + Math.random());
const factA = await api(banker.token, 'POST', `/workstreams/${ws.CAPITAL_STRUCTURE}/facts`,
  { factKey: 'issuer.promoter_ownership_pct', label: 'Promoter shareholding', value: '49', unit: '%',
    validFrom: '2026-09-01T00:00:00Z' });
await api(banker.token, 'POST', `/facts/${factA.id}/evidence-links`, { evidenceId: regA.id });
const regB = await upload(ws.CAPITAL_STRUCTURE, 'statement-as-of-30-september.txt', 'Promoter holding 51% ' + Math.random());
const factB = await api(banker.token, 'POST', `/workstreams/${ws.CAPITAL_STRUCTURE}/facts`,
  { factKey: 'issuer.promoter_ownership_pct', label: 'Promoter shareholding', value: '51', unit: '%',
    validFrom: '2026-09-30T00:00:00Z' });
await api(banker.token, 'POST', `/facts/${factB.id}/evidence-links`, { evidenceId: regB.id });

// answer every blocking diligence question except one, which is answered on camera
// a real statement, so the demo can show extraction and the page region behind a value
const pdfForm = new FormData();
pdfForm.append('file', new Blob([fs.readFileSync(`${OUT}/sample-audited-financials.pdf`)], { type: 'application/pdf' }),
  'audited-financial-statements-fy2026.pdf');
pdfForm.append('documentType', 'AUDITED_FINANCIAL_STATEMENT');
const statement = await (await fetch(`${API}/workstreams/${ws.FINANCIAL_DUE_DILIGENCE}/evidence`,
  { method: 'POST', headers: { Authorization: 'Bearer ' + banker.token }, body: pdfForm })).json();
for (let i = 0; i < 40; i++) {
  const current = await api(banker.token, 'GET', `/evidence/${statement.id}`);
  if (current.processingStatus !== 'PENDING' && current.processingStatus !== 'PROCESSING') break;
  await new Promise(r => setTimeout(r, 500));
}
const extracted = await api(banker.token, 'GET', `/workstreams/${ws.FINANCIAL_DUE_DILIGENCE}/candidate-facts`);
console.log('candidates from the statement:', extracted.map(c => `${c.label}=${c.value}`).join(', '));

const diligenceFile = await upload(ws.CAPITAL_STRUCTURE, 'diligence-file-notes.txt', 'notes ' + Math.random());
const questions = await api(banker.token, 'GET', `/transactions/${tx.id}/diligence-questions`);
const onCamera = questions.find(q => q.code === 'SITE_VISIT');
for (const q of questions) {
  if (q.status === 'OPEN' && q.severity === 'BLOCKING' && q.id !== onCamera.id) {
    await api(banker.token, 'PUT', `/diligence-questions/${q.id}/answer`,
      { answer: 'Reviewed with the issuer and documented in the linked file.', evidenceIds: [diligenceFile.id] });
  }
}
const conflicts = await api(banker.token, 'GET', `/transactions/${tx.id}/conflicts`);
console.log('seeded. conflicts:', conflicts.length, 'questions:', questions.length);
fs.writeFileSync(`${OUT}/demo-seed.json`, JSON.stringify({ tx: tx.id, banker: banker.user.email, admin: admin.user.email }));

// ---------------------------------------------------------------- record
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome-stable' });
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  recordVideo: { dir: `${OUT}/video`, size: { width: 1440, height: 900 } },
});
// a visible pointer, so a viewer can follow what is being clicked
await context.addInitScript(() => {
  window.addEventListener('DOMContentLoaded', () => {
    const dot = document.createElement('div');
    dot.style.cssText = 'position:fixed;z-index:2147483647;width:18px;height:18px;border-radius:50%;'
      + 'background:rgba(255,255,255,0.85);box-shadow:0 0 0 2px rgba(0,0,0,0.55),0 0 12px rgba(255,255,255,0.5);'
      + 'pointer-events:none;transition:transform .08s linear;left:0;top:0;margin:-9px 0 0 -9px';
    document.body.appendChild(dot);
    document.addEventListener('mousemove', (e) => {
      dot.style.transform = `translate(${e.clientX}px, ${e.clientY}px)`;
    });
  });
});
const page = await context.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));

const pause = (ms) => page.waitForTimeout(ms);
async function point(locator) {
  const box = await locator.first().boundingBox();
  if (box) await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 18 });
}
async function clickSlowly(locator, settle = 900) {
  await point(locator);
  await pause(350);
  await locator.first().click();
  await pause(settle);
}
async function typeSlowly(locator, text, delay = 55) {
  await point(locator);
  await locator.first().click();
  await locator.first().type(text, { delay });
}
async function scrollTo(y, ms = 700) {
  await page.evaluate((top) => window.scrollTo({ top, behavior: 'smooth' }), y);
  await pause(ms);
}

started = t0();

// --- 1. sign in (0:00)
mark('Scene 1 — the product, signing in');
await page.goto(`${B}/login`);
await pause(1800);
await typeSlowly(page.getByLabel(/email/i), banker.user.email, 18);
await typeSlowly(page.getByLabel(/password/i), 'password123', 30);
await clickSlowly(page.getByRole('button', { name: /log in|sign in/i }), 1800);

// --- 2. the home page and what it offers (0:20)
mark('Scene 2 — home: one answer starts a deal');
await pause(3500);
await point(page.getByPlaceholder(/name of the company/i));
await pause(2500);

// --- 3. open the transaction (0:30)
mark('Scene 3 — the transaction and its stage');
await page.goto(`${B}/transactions/${tx.id}`);
await page.getByText('Diligence workbench').waitFor();
await pause(4000);
await point(page.getByText('Stage'));
await pause(3500);

// --- 4. the workbench (0:45)
mark('Scene 4 — the workbench: what stands in the way');
await scrollTo(560, 900);
await pause(8000);
await scrollTo(1050, 900);
await pause(7000);

// --- 5. documents (1:10)
mark('Scene 5 — documents and extraction');
await scrollTo(0, 500);
await clickSlowly(page.getByRole('button', { name: /^Documents/ }), 1500);
await pause(7000);
await scrollTo(380, 800);
await pause(5000);
await scrollTo(0, 500);

// --- 6. the machine proposes, a person decides (1:25)
mark('Scene 6 — candidates extracted from the statement, awaiting a person');
await page.goto(`${B}/workstreams/${ws.FINANCIAL_DUE_DILIGENCE}`);
await pause(3000);
await scrollTo(620, 900);
await pause(9000);
// the candidate's label lives in an input value, so match the card by that input
const acceptRevenue = page.locator('.candidate-fact-card')
  .filter({ has: page.locator('input[value="Total Revenue"]') })
  .getByRole('button', { name: /^Accept$/ }).first();
if (await acceptRevenue.count()) {
  await clickSlowly(acceptRevenue, 3500);
  await pause(4000);
} else {
  throw new Error('the Total Revenue candidate was not found on screen');
}

// the accepted value, traced back to the page it came from
mark('Scene 6b — the value, traced to the page it came from');
const accepted = (await api(banker.token, 'GET', `/workstreams/${ws.FINANCIAL_DUE_DILIGENCE}/candidate-facts`))
  .find(c => c.status === 'ACCEPTED' && c.resultingFactId);
if (accepted) {
  await api(auditor.token, 'POST', `/facts/${accepted.resultingFactId}/verify`);
  await page.goto(`${B}/facts/${accepted.resultingFactId}/trace`);
  await pause(6000);
  await scrollTo(380, 900);
  await pause(8000);
}

// --- 7. conflict (2:00)
mark('Scene 7 — two sources disagree');
await page.goto(`${B}/transactions/${tx.id}`);
await page.getByText('Diligence workbench').waitFor();
await clickSlowly(page.getByRole('button', { name: /^Conflicts/ }), 1800);
await pause(10000);
await clickSlowly(page.getByRole('button', { name: /^Resolve$/ }), 1200);
await pause(2500);
await page.selectOption('select', { index: 1 });
await pause(1200);
await typeSlowly(page.locator('textarea').first(),
  'Board resolution effective 1 September; the 30 September statement predates registration.', 22);
await pause(900);
const consider = page.locator('.checkbox-label input').first();
if (await consider.count()) { await clickSlowly(consider, 500); }
await clickSlowly(page.getByRole('button', { name: /record resolution/i }), 3500);
await pause(6000);

// the value that stands is verified by the auditor, from their own account
const kept = (await api(banker.token, 'GET', `/transactions/${tx.id}/facts`))
  .find(f => f.factKey === 'issuer.promoter_ownership_pct' && f.status === 'DRAFT');
if (kept) await api(auditor.token, 'POST', `/facts/${kept.id}/verify`);

// --- 8. diligence questions (2:50)
mark('Scene 8 — the diligence questions');
await clickSlowly(page.getByRole('button', { name: /^Diligence/ }), 1800);
await pause(6500);
await scrollTo(900, 900);
await pause(5000);

// answer the one question left open, with the document it rests on
const answerButton = page.getByRole('row').filter({ hasText: /site visit/i })
  .getByRole('button', { name: /^Answer$/ }).first();
if (await answerButton.count()) {
  await clickSlowly(answerButton, 1500);
  await typeSlowly(page.locator('textarea').first(),
    'Site visited on 12 September; plant running two shifts, capacity consistent with the projections.', 20);
  await pause(1200);
  const box = page.locator('.checkbox-label input').first();
  if (await box.count()) await clickSlowly(box, 600);
  await pause(1500);
  await clickSlowly(page.getByRole('button', { name: /record answer/i }), 3500);
  await pause(4000);
}

// a second person accepts the answer, from their own account
const pending = (await api(banker.token, 'GET', `/transactions/${tx.id}/diligence-questions`))
  .find(q => q.status === 'ANSWERED');
if (pending) await api(auditor.token, 'POST', `/diligence-questions/${pending.id}/accept`);

// --- 9. the DRHP, written from verified facts
mark('Scene 9 — writing a disclosure from verified facts');
await scrollTo(0, 400);
await clickSlowly(page.getByRole('button', { name: /^DRHP/ }), 1500);
await clickSlowly(page.getByRole('button', { name: /new disclosure/i }), 1200);
await typeSlowly(page.getByLabel(/section code/i), 'CAPITAL_STRUCTURE', 30);
await typeSlowly(page.getByLabel(/^title/i), 'Capital structure', 35);
await typeSlowly(page.locator('textarea').first(), 'The post-issue paid-up capital of the company is ', 28);
await clickSlowly(page.getByRole('button', { name: /insert value/i }), 900);
await typeSlowly(page.getByPlaceholder(/which value/i), 'post-issue', 60);
await pause(1600);
await clickSlowly(page.locator('.fact-picker-list button').first(), 1500);
await pause(2000);
await clickSlowly(page.getByRole('button', { name: /create disclosure/i }), 3500);
await pause(2500);

// --- 10. compile (4:00)
mark('Scene 10 — compiling the filing copy');
await clickSlowly(page.getByRole('button', { name: /compile final filing/i }), 6000);
await pause(6000);
await scrollTo(760, 900);
await pause(7000);

// the provenance behind the compiled copy
mark('Scene 10b — the provenance manifest');
const root = page.locator('.merkle-badge').first();
if (await root.count()) {
  await clickSlowly(root, 3000);
  await pause(8000);
  const close = page.getByRole('button', { name: /^Close$/ }).first();
  if (await close.count()) await clickSlowly(close, 1000);
}

// --- 11. filing: approvals and the package (4:25)
mark('Scene 11 — approvals and the filing package');
await scrollTo(0, 400);
await clickSlowly(page.getByRole('button', { name: /^Filing/ }), 2000);
await pause(3000);
const approve = page.getByRole('button', { name: /approve drhp/i });
if (await approve.count()) await clickSlowly(approve, 3500);
await pause(3000);
const docId = (await api(banker.token, 'GET', `/transactions/${tx.id}/filing`)).documentId;
await api(admin.token, 'POST', `/drhp/versions/${docId}/approvals`);
await page.reload();
await clickSlowly(page.getByRole('button', { name: /^Filing/ }), 1800);
await pause(2500);
const assemble = page.getByRole('button', { name: /assemble filing package/i });
if (await assemble.count()) await clickSlowly(assemble, 4000);
await pause(8000);

mark('Scene 11b — what a change would now reach');
await page.goto(`${B}/workstreams/${ws.CAPITAL_STRUCTURE}`);
await pause(2500);
await scrollTo(200, 800);
const correctCapital = page.getByRole('row').filter({ hasText: /Post-issue paid-up capital/i })
  .getByRole('button', { name: /^Correct$/ }).first();
if (await correctCapital.count()) {
  await clickSlowly(correctCapital, 2500);
  await pause(9000);
}
await page.goto(`${B}/transactions/${tx.id}`);
await page.getByText('Diligence workbench').waitFor();
await pause(1500);

// --- 12. audit trail and close (4:50)
mark('Scene 12 — the audit trail');
await clickSlowly(page.getByRole('button', { name: /^Audit/ }), 2000);
await pause(7000);
await scrollTo(600, 900);
await pause(7000);

mark('Scene 13 — where the deal now stands');
await scrollTo(0, 500);
await clickSlowly(page.getByRole('button', { name: /^Overview/ }), 2000);
await pause(9000);
mark('end');

console.log('page errors:', errors.slice(0, 3));
await context.close();
await browser.close();
fs.writeFileSync(`${OUT}/demo-marks.json`, JSON.stringify(marks, null, 2));
