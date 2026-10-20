import fs from 'fs';

const BASE = 'http://localhost:8080';
const API = `${BASE}/api`;
const PASSWORD = 'haroon@trinity';

async function api(token, method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  }
  return text ? JSON.parse(text) : null;
}

async function uploadPdf(token, workstreamId, filePath, fileName) {
  const form = new FormData();
  const fileBytes = fs.readFileSync(filePath);
  const blob = new Blob([fileBytes], { type: 'application/pdf' });
  form.append('file', blob, fileName);
  form.append('documentType', 'AUDITED_FINANCIAL_STATEMENT');

  const res = await fetch(`${API}/workstreams/${workstreamId}/evidence`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: form,
  });
  const text = await res.text();
  if (!res.ok) {
    if (res.status === 409 && text.includes('DUPLICATE_EVIDENCE')) {
      const match = text.match(/\(([0-9a-f-]{36})\)/);
      if (match) return { id: match[1], fileName };
    }
    throw new Error(`Upload ${fileName} -> ${res.status}: ${text.slice(0, 300)}`);
  }
  return JSON.parse(text);
}

async function main() {
  console.log('1. Logging in as Haroon (Issuer Admin) and Amina (Auditor)...');
  const haroonLogin = await api(null, 'POST', '/auth/login', {
    email: 'haroon@trinitycapital.com',
    password: PASSWORD,
  });
  const aminaLogin = await api(null, 'POST', '/auth/login', {
    email: 'amina.qasim@alqasimaudit.com',
    password: PASSWORD,
  });

  const txs = await api(haroonLogin.token, 'GET', '/transactions');
  const trinityTx = txs.find(t => t.name.includes('Trinity Capital') || t.companyName.includes('Trinity'));
  if (!trinityTx) throw new Error('Trinity transaction not found');
  console.log(`✓ Using Transaction: ${trinityTx.id} - ${trinityTx.name}`);

  const workstreams = await api(haroonLogin.token, 'GET', `/transactions/${trinityTx.id}/workstreams`);
  const finWs = workstreams.find(w => w.type === 'FINANCIAL_DUE_DILIGENCE');
  if (!finWs) throw new Error('Financial Due Diligence workstream not found');
  console.log(`✓ Using Financial Workstream: ${finWs.id}`);

  console.log('2. Uploading sample 4-page Audited Financial Report PDF...');
  const pdfPath = '/home/haroon/Desktop/syndicate/sample-audit-report-fy26.pdf';
  const evidence = await uploadPdf(haroonLogin.token, finWs.id, pdfPath, 'trinity-audited-financial-report-fy26.pdf');
  console.log(`✓ Evidence Uploaded: ${evidence.id} (${evidence.fileName})`);

  // Wait 1.5s for async extraction to process
  console.log('3. Waiting for document ingestion & extraction...');
  await new Promise(r => setTimeout(r, 1500));

  console.log('4. Checking spatial manifest...');
  const manifest = await api(haroonLogin.token, 'GET', `/evidence/${evidence.id}/manifest`);
  console.log(`✓ Manifest loaded: ${manifest.pageCount} pages, ${manifest.anchors.length} anchors`);

  // Get candidate facts for this workstream
  const candidates = await api(haroonLogin.token, 'GET', `/workstreams/${finWs.id}/candidate-facts`);
  const docCandidates = candidates.filter(c => c.evidenceId === evidence.id);
  console.log(`✓ Candidate facts on document: ${docCandidates.length}`);

  // Accept one candidate fact as verified
  const revCandidate = docCandidates.find(c => c.label.toLowerCase().includes('revenue') || c.value.includes('82,45,00,000') || c.pageNumber === 3);
  if (revCandidate && revCandidate.status === 'PENDING') {
    console.log(`5. Accepting candidate fact: ${revCandidate.label} (${revCandidate.value})...`);
    const accepted = await api(haroonLogin.token, 'POST', `/candidate-facts/${revCandidate.id}/accept`, {
      label: 'Revenue from Operations',
      value: '82,45,00,000',
      period: 'FY2026',
      reviewNote: 'Verified against Audited P&L Schedule 18',
    });
    console.log(`✓ Accepted fact id: ${accepted.resultingFactId}`);

    // Verify fact as auditor
    if (accepted.resultingFactId) {
      await api(aminaLogin.token, 'POST', `/facts/${accepted.resultingFactId}/verify`);
      console.log('✓ Verified fact with Auditor sign-off');
    }
  }

  console.log('=== Spatial evidence seeding ready! ===');
  console.log(`Evidence ID: ${evidence.id}`);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
