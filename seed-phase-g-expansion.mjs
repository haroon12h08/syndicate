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

async function main() {
  console.log('1. Logging in as Haroon (Issuer Admin)...');
  const login = await api(null, 'POST', '/auth/login', {
    email: 'haroon@trinitycapital.com',
    password: PASSWORD,
  });
  const token = login.token;

  const txs = await api(token, 'GET', '/transactions');
  const trinityTx = txs.find(t => t.name.includes('Trinity Capital') || t.companyName?.includes('Trinity'));
  if (!trinityTx) throw new Error('Trinity transaction not found');
  console.log(`✓ Using Transaction: ${trinityTx.id} - ${trinityTx.name}`);

  // 2. Check observations
  console.log('2. Checking regulatory observations...');
  const observations = await api(token, 'GET', `/transactions/${trinityTx.id}/observations`);
  let openObs = observations.find(o => o.status === 'OPEN' || !o.response);
  if (!openObs) {
    console.log('Creating an open observation for AI draft response...');
    openObs = await api(token, 'POST', `/transactions/${trinityTx.id}/observations`, {
      authority: 'BSE SME Listing Department',
      reference: 'BSE/SME/2026/TRINITY-04',
      receivedDate: '2026-10-15',
      responseDeadline: '2026-11-05',
      observation: 'Please clarify the basis for EBITDA margin expansion from 18.2% to 22.4% in FY26 given increased raw material expenditures, citing primary audited ledger extracts and Board confirmations.',
      sectionCode: 'Financial Information',
      severity: 'HIGH',
      factIds: [],
      evidenceIds: [],
      disclosureIds: []
    });
    console.log(`✓ Created observation: ${openObs.id}`);
  } else {
    console.log(`✓ Found existing open observation: ${openObs.id}`);
  }

  // 3. Generate suggestions
  console.log('3. Running AI scan to ensure all suggestions exist...');
  const gen = await api(token, 'POST', `/transactions/${trinityTx.id}/suggestions/generate`);
  console.log(`✓ Generated: ${gen.generatedCount}, Pending total: ${gen.pendingCount}`);

  // 4. Verify suggestions
  const suggs = await api(token, 'GET', `/transactions/${trinityTx.id}/suggestions`);
  console.log(`✓ Active suggestions: ${suggs.length}`);
  suggs.forEach(s => {
    console.log(`  - [${s.severity}] (${s.suggestionType}) ${s.title}`);
  });

  console.log('\nSeed Phase G Expansion finished successfully!');
}

main().catch(err => {
  console.error('Seed failed:', err);
  process.exit(1);
});
