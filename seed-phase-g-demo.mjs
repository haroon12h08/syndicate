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

async function uploadText(token, workstreamId, fileName, textContent, docType = 'OTHER') {
  const form = new FormData();
  const blob = new Blob([textContent], { type: 'text/plain' });
  form.append('file', blob, fileName);
  form.append('documentType', docType);

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
  console.log('1. Logging in as Haroon (Issuer Admin)...');
  const haroonLogin = await api(null, 'POST', '/auth/login', {
    email: 'haroon@trinitycapital.com',
    password: PASSWORD,
  });

  const txs = await api(haroonLogin.token, 'GET', '/transactions');
  const trinityTx = txs.find(t => t.name.includes('Trinity Capital') || t.companyName.includes('Trinity'));
  if (!trinityTx) throw new Error('Trinity transaction not found');
  console.log(`✓ Using Transaction: ${trinityTx.id} - ${trinityTx.name}`);

  const workstreams = await api(haroonLogin.token, 'GET', `/transactions/${trinityTx.id}/workstreams`);
  const finWs = workstreams.find(w => w.type === 'FINANCIAL_DUE_DILIGENCE') || workstreams[0];
  console.log(`✓ Using Workstream: ${finWs.id} (${finWs.name})`);

  // Upload Board Resolution to test AI classification suggestion
  const resolutionText = `
TRINITY CAPITAL LIMITED
CERTIFIED TRUE COPY OF THE RESOLUTION PASSED AT THE EXTRAORDINARY GENERAL MEETING (EGM)
HELD ON MAY 2, 2026 AT THE REGISTERED OFFICE.

RESOLVED THAT pursuant to the provisions of Section 62(1)(c) and all other applicable provisions
of the Companies Act, 2013, the consent of the Members of the Company be and is hereby accorded
to approve the Initial Public Offering (IPO) of up to 45,00,000 Equity Shares.

FURTHER RESOLVED THAT the Restated EBITDA of Rs. 42.18 Cr and Restated Net Profit of Rs. 18.50 Cr
for the financial year ended March 31, 2026 be approved for inclusion in the DRHP.
  `.trim();

  console.log('2. Uploading Board Resolution evidence as OTHER...');
  const uploaded = await uploadText(
    haroonLogin.token,
    finWs.id,
    `trinity-board-resolution-${Date.now().toString().slice(-4)}.txt`,
    resolutionText,
    'OTHER'
  );
  console.log(`✓ Uploaded evidence: ${uploaded.id} (${uploaded.fileName})`);

  console.log('3. Waiting for extraction to settle...');
  let settled = false;
  for (let i = 0; i < 20; i++) {
    await new Promise(r => setTimeout(r, 1000));
    const ev = await api(haroonLogin.token, 'GET', `/evidence/${uploaded.id}`);
    if (ev.processingStatus === 'COMPLETE') {
      settled = true;
      console.log(`✓ Evidence processed! Suggested Document Type: ${ev.suggestedDocumentType}`);
      break;
    }
  }

  if (!settled) throw new Error('Evidence processing timed out');

  // Verify candidate facts
  const candidates = await api(haroonLogin.token, 'GET', `/workstreams/${finWs.id}/candidate-facts?status=PENDING`);
  const aiCandidates = candidates.filter(c => c.source === 'AI');
  console.log(`✓ Pending candidates total: ${candidates.length}, AI candidates: ${aiCandidates.length}`);
  if (aiCandidates.length > 0) {
    console.log(`  Sample AI Candidate: ${aiCandidates[0].label} = ${aiCandidates[0].value} (Confidence: ${aiCandidates[0].confidence}, Artifact ID: ${aiCandidates[0].aiArtifactId})`);
  }
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
