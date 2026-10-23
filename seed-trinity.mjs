import fs from 'fs';

const B = 'http://localhost:8080';
const API = `${B}/api`;
const PASSWORD = 'haroon@trinity';

const api = async (token, method, path, body, clientIp = '127.0.0.1') => {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'X-Forwarded-For': clientIp,
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  }
  return text ? JSON.parse(text) : null;
};

async function registerOrLogin(email, fullName, orgName, orgType, clientIp) {
  try {
    const reg = await api(null, 'POST', '/auth/register', {
      email,
      password: PASSWORD,
      fullName,
      organizationName: orgName,
      organizationType: orgType,
    }, clientIp);
    return { token: reg.token, user: reg.user, organization: reg.organization };
  } catch (err) {
    if (err.message && err.message.includes('already exists')) {
      const login = await api(null, 'POST', '/auth/login', { email, password: PASSWORD }, clientIp);
      const orgs = await api(login.token, 'GET', '/organizations');
      return { token: login.token, user: login.user, organization: orgs[0] };
    }
    throw err;
  }
}

async function upload(token, workstreamId, name, textOrBuffer, type = 'OTHER', mime = 'text/plain') {
  const form = new FormData();
  const blob = typeof textOrBuffer === 'string'
    ? new Blob([textOrBuffer], { type: mime })
    : new Blob([textOrBuffer], { type: mime });
  form.append('file', blob, name);
  form.append('documentType', type);
  const res = await fetch(`${API}/workstreams/${workstreamId}/evidence`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: form,
  });
  const text = await res.text();
  if (!res.ok) {
    if (res.status === 409 && text.includes('DUPLICATE_EVIDENCE')) {
      const match = text.match(/\(([0-9a-f-]{36})\)/);
      if (match) {
        return { id: match[1], fileName: name };
      }
    }
    throw new Error(`Upload ${name} -> ${res.status}: ${text.slice(0, 300)}`);
  }
  return JSON.parse(text);
}

async function establishFact(token, auditorToken, workstreamId, factKey, label, value, unit = 'INR', period = null, validFrom = null, evId = null, txId = null) {
  if (txId) {
    try {
      const existingFacts = await api(token, 'GET', `/transactions/${txId}/facts`);
      const found = existingFacts.find(f => f.factKey === factKey && (period ? f.period === period : true) && f.status !== 'SUPERSEDED');
      if (found) {
        if (found.status === 'DRAFT' && auditorToken) {
          try { await api(auditorToken, 'POST', `/facts/${found.id}/verify`); } catch(e){}
        }
        return found;
      }
    } catch (e) {}
  }
  const fact = await api(token, 'POST', `/workstreams/${workstreamId}/facts`, {
    factKey,
    label,
    value,
    unit,
    period,
    validFrom,
  });
  if (evId) {
    try {
      await api(token, 'POST', `/facts/${fact.id}/evidence-links`, { evidenceId: evId });
      await api(token, 'PUT', `/evidence/${evId}/quality`, { quality: 'ACCEPTABLE' });
    } catch (e) {
      console.warn(`Link evidence to ${label}: ${e.message}`);
    }
  }
  if (auditorToken) {
    try {
      await api(auditorToken, 'POST', `/facts/${fact.id}/verify`);
    } catch (e) {
      console.warn(`Could not verify fact ${label}: ${e.message}`);
    }
  }
  return fact;
}

async function main() {
  console.log('=== SEEDING TRINITY CAPITAL IPO DATABASE ===');

  // 1. Define all 16 participants
  const membersDef = [
    {
      key: 'haroon',
      email: 'haroon@trinitycapital.com',
      fullName: 'Haroon Al-Mansoor',
      orgName: 'Trinity Capital Limited',
      orgType: 'ISSUER',
      txRole: 'ISSUER_ADMIN',
    },
    {
      key: 'fatima',
      email: 'fatima.zahra@trinitycapital.com',
      fullName: 'Fatima Al-Zahra',
      orgName: 'Trinity Capital Limited - Finance',
      orgType: 'ISSUER',
      txRole: 'CFO',
    },
    {
      key: 'omar',
      email: 'omar.farooq@trinitycapital.com',
      fullName: 'Omar Al-Farooq',
      orgName: 'Trinity Capital Limited - Promoters',
      orgType: 'ISSUER',
      txRole: 'PROMOTER',
    },
    {
      key: 'zaid',
      email: 'zaid.bakr@trinitycapital.com',
      fullName: 'Zaid Al-Bakr',
      orgName: 'Trinity Capital Limited - Secretarial',
      orgType: 'COMPANY_SECRETARY',
      txRole: 'COMPANY_SECRETARY',
    },
    {
      key: 'khaled',
      email: 'khaled.salem@trinitycapital.com',
      fullName: 'Khaled Al-Salem',
      orgName: 'Trinity Capital Limited - Corporate Advisory',
      orgType: 'OTHER_ADVISOR',
      txRole: 'ADVISOR',
    },
    {
      key: 'tariq',
      email: 'tariq.hashimi@almadinacapital.com',
      fullName: 'Tariq Al-Hashimi',
      orgName: 'Al-Madina Investment Banking',
      orgType: 'MERCHANT_BANKER',
      txRole: 'LEAD_BANKER',
    },
    {
      key: 'hamza',
      email: 'hamza.ghamdi@almadinacapital.com',
      fullName: 'Hamza Al-Ghamdi',
      orgName: 'Al-Madina Due Diligence Group',
      orgType: 'MERCHANT_BANKER',
      txRole: 'DUE_DILIGENCE_TEAM',
    },
    {
      key: 'mariam',
      email: 'mariam.husseini@almadinacapital.com',
      fullName: 'Mariam Al-Husseini',
      orgName: 'Al-Madina Equity Syndication',
      orgType: 'MERCHANT_BANKER',
      txRole: 'DUE_DILIGENCE_TEAM',
    },
    {
      key: 'layla',
      email: 'layla.khatib@alkhatiblaw.com',
      fullName: 'Layla Al-Khatib',
      orgName: 'Al-Khatib & Associates Legal Counsel',
      orgType: 'LEGAL_COUNSEL',
      txRole: 'LEAD_LAWYER',
    },
    {
      key: 'youssef',
      email: 'youssef.banna@alkhatiblaw.com',
      fullName: 'Youssef Al-Banna',
      orgName: 'Al-Khatib Corporate Practice',
      orgType: 'LEGAL_COUNSEL',
      txRole: 'LEGAL_ASSOCIATE',
    },
    {
      key: 'amina',
      email: 'amina.qasim@alqasimaudit.com',
      fullName: 'Amina Al-Qasim',
      orgName: 'Al-Qasim & Partners Chartered Accountants',
      orgType: 'STATUTORY_AUDITOR',
      txRole: 'AUDITOR',
    },
    {
      key: 'noor',
      email: 'noor.din@alqasimaudit.com',
      fullName: 'Noor Al-Din',
      orgName: 'Al-Qasim Assurance Services',
      orgType: 'STATUTORY_AUDITOR',
      txRole: 'AUDITOR',
    },
    {
      key: 'bilal',
      email: 'bilal.masri@masritax.com',
      fullName: 'Bilal Al-Masri',
      orgName: 'Al-Masri Tax Advisors',
      orgType: 'CA_TAX_ADVISOR',
      txRole: 'TAX_ADVISOR',
    },
    {
      key: 'ibrahim',
      email: 'ibrahim.nasser@amanaregistrar.com',
      fullName: 'Ibrahim Al-Nasser',
      orgName: 'Al-Amana Share Registrars',
      orgType: 'REGISTRAR',
      txRole: 'ADVISOR',
    },
    {
      key: 'samira',
      email: 'samira.tamimi@tamimimarkets.com',
      fullName: 'Samira Al-Tamimi',
      orgName: 'Tamimi Securities & Market Makers',
      orgType: 'MARKET_MAKER',
      txRole: 'ADVISOR',
    },
    {
      key: 'mustafa',
      email: 'mustafa.najjar@alnooradvisory.com',
      fullName: 'Mustafa Al-Najjar',
      orgName: 'Al-Noor Advisory & Compliance',
      orgType: 'REGULATORY_CONSULTANT',
      txRole: 'REGULATORY_CONSULTANT',
    },
  ];

  console.log('1. Registering/authenticating all 16 syndicate members...');
  const users = {};
  for (let i = 0; i < membersDef.length; i++) {
    const m = membersDef[i];
    const clientIp = `10.0.1.${i + 1}`;
    const res = await registerOrLogin(m.email, m.fullName, m.orgName, m.orgType, clientIp);
    users[m.key] = { ...m, ...res };
    console.log(`  ✓ ${m.fullName.padEnd(22)} (${m.email}) - ${m.txRole}`);
  }

  const haroon = users['haroon'];
  const tariq = users['tariq'];
  const amina = users['amina'];
  const layla = users['layla'];

  // 2. Start the transaction if not already created
  console.log('\n2. Starting or finding Trinity Capital Limited SME IPO...');
  const myTxns = await api(haroon.token, 'GET', '/transactions');
  let tx = myTxns.find(t => t.name.includes('Trinity Capital'));
  if (!tx) {
    tx = await api(haroon.token, 'POST', '/transactions/start', {
      companyName: 'Trinity Capital Limited',
      cin: 'U65999MH2018PLC312845',
    });
    console.log(`  ✓ Transaction opened: ${tx.id} - ${tx.name}`);
  } else {
    console.log(`  ✓ Using existing transaction: ${tx.id} - ${tx.name}`);
  }

  // 3. Add all members to the transaction
  console.log('\n3. Enrolling all 15 collaborating members into transaction memberships...');
  const existingMembers = await api(haroon.token, 'GET', `/transactions/${tx.id}/memberships`);
  const memberEmails = new Set(existingMembers.map(m => m.user.email));

  for (const m of membersDef) {
    if (m.key === 'haroon') continue;
    const u = users[m.key];
    if (memberEmails.has(u.email)) {
      console.log(`  - ${u.fullName} already enrolled`);
      continue;
    }
    try {
      await api(haroon.token, 'POST', `/transactions/${tx.id}/memberships`, {
        organizationId: u.organization.id,
        email: u.email,
        role: m.txRole,
      });
      console.log(`  ✓ Enrolled ${u.fullName} as ${m.txRole}`);
    } catch (e) {
      console.warn(`  ! Could not enroll ${u.fullName}: ${e.message}`);
    }
  }

  // 4. Setup Workstreams
  console.log('\n4. Checking & setting up full suite of IPO workstreams...');
  let workstreams = await api(haroon.token, 'GET', `/transactions/${tx.id}/workstreams`);
  const wsByType = Object.fromEntries(workstreams.map(w => [w.type, w.id]));

  const neededWorkstreams = [
    { type: 'CORPORATE_SECRETARIAL', description: 'Statutory registers, board resolutions, ROC filings, secretarial audit' },
    { type: 'TAX', description: 'Direct and indirect tax assessments, Section 143(3) orders, GST reconciliation' },
    { type: 'MATERIAL_CONTRACTS', description: 'Material customer contracts (>5% revenue), vendor MSAs, technology licenses' },
    { type: 'LITIGATION', description: 'Litigation search, promoter civil proceedings, statutory penalty checks' },
  ];

  for (const nw of neededWorkstreams) {
    if (!wsByType[nw.type]) {
      const createdWs = await api(haroon.token, 'POST', `/transactions/${tx.id}/workstreams`, nw);
      wsByType[nw.type] = createdWs.id;
      console.log(`  ✓ Created workstream: ${nw.type}`);
    }
  }

  // 5. Upload Evidence Documents
  console.log('\n5. Uploading authentic evidence documents...');
  const samplePdfPath = '/home/haroon/Desktop/syndicate/demo/sample-audited-financials.pdf';
  let samplePdfBuf = null;
  if (fs.existsSync(samplePdfPath)) {
    samplePdfBuf = fs.readFileSync(samplePdfPath);
  }

  // Financial Statements PDF
  const evFinancialStatements = samplePdfBuf
    ? await upload(haroon.token, wsByType.FINANCIAL_DUE_DILIGENCE,
        'trinity-audited-restated-financials-fy24-fy26.pdf', samplePdfBuf, 'AUDITED_FINANCIAL_STATEMENT', 'application/pdf')
    : await upload(haroon.token, wsByType.FINANCIAL_DUE_DILIGENCE,
        'trinity-audited-restated-financials-fy24-fy26.txt',
        'Trinity Capital Limited - Standalone Restated Financial Statements for FY 2023-24, FY 2024-25, and FY 2025-26 as audited by Al-Qasim & Partners. Total Revenue FY26: INR 82,45,00,000. EBITDA FY26: INR 16,80,00,000. Net Profit: INR 11,40,00,000.',
        'AUDITED_FINANCIAL_STATEMENT');
  console.log(`  ✓ Uploaded: Restated Financial Statements (${evFinancialStatements.id})`);

  // Auditor Examination Report
  const evAuditorReport = await upload(amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'independent-auditor-examination-report.txt',
    'Independent Auditor Examination Report on Restated Standalone Financial Statements of Trinity Capital Limited. To the Board of Directors: We have examined the restated financial information in accordance with Part IX of SEBI ICDR Regulations. In our opinion, the restated figures give a true and fair view and comply with applicable accounting standards without qualifications.',
    'CERTIFICATE');
  console.log(`  ✓ Uploaded: Auditor Examination Report (${evAuditorReport.id})`);

  // Working Capital Certificate
  const evWorkingCapital = await upload(haroon.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'working-capital-bank-consortium-certificate.txt',
    'Consortium Banking Certificate: Sanctioned fund-based working capital limit of INR 45,00,00,000 with Lead Bank. Current utilization: INR 31,50,00,000. Standard asset classification with zero defaults.',
    'CERTIFICATE');
  console.log(`  ✓ Uploaded: Working Capital Certificate (${evWorkingCapital.id})`);

  // MGT-1 Register of Members
  const evRegisterMembers = await upload(haroon.token, wsByType.CAPITAL_STRUCTURE,
    'mgt-1-register-of-members-post-bonus.txt',
    'Register of Members (Form MGT-1) under Section 88 of Companies Act, 2013 for Trinity Capital Limited. Total Issued, Subscribed, and Paid-up Equity Capital: 24,500,000 equity shares of face value INR 10 each, fully paid-up. Post-issue capital expanded to 28,700,000 shares upon fresh issue.',
    'GOVERNMENT_FILING');
  console.log(`  ✓ Uploaded: Register of Members MGT-1 (${evRegisterMembers.id})`);

  // BENPOS Depository Report
  const evBenpos = await upload(users['ibrahim'].token, wsByType.CAPITAL_STRUCTURE,
    'depository-benpos-summary-september-2026.txt',
    'NSDL & CDSL Consolidated Beneficiary Position (BENPOS) as of 30 September 2026. 100% promoter equity shareholding dematerialized under ISIN INE928K01018. Promoter Omar Al-Farooq holds 15,000,000 shares (61.22% pre-issue).',
    'CERTIFICATE');
  console.log(`  ✓ Uploaded: Depository BENPOS Report (${evBenpos.id})`);

  // Legal Due Diligence Report
  const evLegalDiligence = await upload(layla.token, wsByType.LEGAL_DUE_DILIGENCE,
    'comprehensive-legal-due-diligence-report-alkhatib.txt',
    'Comprehensive Legal Due Diligence Report issued by Al-Khatib & Associates Legal Counsel. We have reviewed corporate documents, material contracts, title deeds for BKC Corporate Office, employment agreements, and IP trademarks for "Trinity Capital". In our view, the company is in compliance with all regulatory conditions for SME listing.',
    'CERTIFICATE');
  console.log(`  ✓ Uploaded: Legal Due Diligence Report (${evLegalDiligence.id})`);

  // Factory and Environmental Licences
  const evLicences = await upload(haroon.token, wsByType.REGULATORY_DUE_DILIGENCE,
    'factory-inspectorate-and-environmental-consents.txt',
    'Directorate of Industrial Safety & Health (DISH) Factory Licence #MH-MUM-48291, valid through 31 December 2031. Maharashtra Pollution Control Board (MPCB) Consent to Operate under Green Category valid through 31 December 2028.',
    'LICENCE');
  console.log(`  ✓ Uploaded: Factory & Environmental Licences (${evLicences.id})`);

  // Capital Board Resolution & Issue Authorisation in CAPITAL_STRUCTURE
  const evCapitalBoardRes = await upload(haroon.token, wsByType.CAPITAL_STRUCTURE,
    'board-resolution-capital-alteration-issue-terms.txt',
    'Certified Extract of Resolution passed by the Board of Directors of Trinity Capital Limited: Resolved that fresh issue of 4,200,000 equity shares and offer for sale of 1,800,000 equity shares be offered within an indicative price band of INR 125 to INR 132 per equity share.',
    'BOARD_RESOLUTION');
  console.log(`  ✓ Uploaded: Capital Board Resolution (${evCapitalBoardRes.id})`);

  // Board Resolution for IPO in CORPORATE_SECRETARIAL
  const evBoardRes = await upload(users['zaid'].token, wsByType.CORPORATE_SECRETARIAL,
    'board-resolution-ipo-approval-and-rhp.txt',
    'Certified True Copy of Resolution passed by the Board of Directors of Trinity Capital Limited on 12 August 2026: Resolved that initial public offering of up to 4,200,000 fresh equity shares and offer for sale of up to 1,800,000 equity shares be undertaken on SME Platform of BSE, subject to regulatory approvals.',
    'BOARD_RESOLUTION');
  console.log(`  ✓ Uploaded: Board Resolution for IPO (${evBoardRes.id})`);

  // Tax Assessment Orders
  const evTaxOrders = await upload(users['bilal'].token, wsByType.TAX,
    'income-tax-assessment-orders-ay24-ay26.txt',
    'Income Tax Department - National Faceless Assessment Centre: Assessment orders under Section 143(3) r.w.s 144B for Assessment Years 2023-24, 2024-25, and 2025-26. Total additions: NIL. Outstanding demand: NIL. Valid PAN: AAACT9821L.',
    'GOVERNMENT_FILING');
  console.log(`  ✓ Uploaded: Tax Assessment Orders (${evTaxOrders.id})`);

  // Material Customer MSAs
  const evMaterialContracts = await upload(layla.token, wsByType.MATERIAL_CONTRACTS,
    'tier-1-material-client-master-services-agreements.txt',
    'Summary Schedule of Material Contracts exceeding 5% materiality threshold: 1) Master Services Agreement with Gulf Sovereign FinCorp (18.4% FY26 revenue), 2) Financial Cloud Technology Agreement with Emirates Capital Partners (12.1% FY26 revenue). No adverse termination clauses.',
    'CONTRACT');
  console.log(`  ✓ Uploaded: Material Customer Contracts (${evMaterialContracts.id})`);

  // 6. Establish and verify grounded facts
  console.log('\n6. Establishing grounded facts across financial, legal, and capital structure...');
  const fPostCapital = await establishFact(haroon.token, amina.token, wsByType.CAPITAL_STRUCTURE,
    'issue.post_issue_paid_up_capital', 'Post-issue paid-up capital', '245000000', 'INR', null, null, evRegisterMembers.id, tx.id);
  const fFreshIssue = await establishFact(haroon.token, amina.token, wsByType.CAPITAL_STRUCTURE,
    'issue.fresh_issue_shares', 'Fresh issue shares', '4200000', null, null, null, evCapitalBoardRes.id, tx.id);
  const fOfs = await establishFact(haroon.token, amina.token, wsByType.CAPITAL_STRUCTURE,
    'issue.ofs_shares', 'Offer for sale shares', '1800000', null, null, null, evBenpos.id, tx.id);
  const fFaceValue = await establishFact(haroon.token, amina.token, wsByType.CAPITAL_STRUCTURE,
    'issuer.face_value', 'Face value per share', '10', 'INR', null, null, evRegisterMembers.id, tx.id);
  const fPrice = await establishFact(tariq.token, amina.token, wsByType.CAPITAL_STRUCTURE,
    'issue.price', 'Issue price per share', '128', 'INR', null, null, evCapitalBoardRes.id, tx.id);

  // Financial facts (3 years of restated figures)
  const fRev26 = await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.revenue', 'Revenue from operations', '824500000', 'INR', 'FY2026', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.revenue', 'Revenue from operations', '682000000', 'INR', 'FY2025', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.revenue', 'Revenue from operations', '541000000', 'INR', 'FY2024', null, evFinancialStatements.id, tx.id);

  const fEbitda26 = await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.ebitda', 'EBITDA', '168000000', 'INR', 'FY2026', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.ebitda', 'EBITDA', '132000000', 'INR', 'FY2025', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.ebitda', 'EBITDA', '98000000', 'INR', 'FY2024', null, evFinancialStatements.id, tx.id);

  const fPat26 = await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.pat', 'Profit after tax', '114000000', 'INR', 'FY2026', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.pat', 'Profit after tax', '88000000', 'INR', 'FY2025', null, evFinancialStatements.id, tx.id);
  await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.pat', 'Profit after tax', '62000000', 'INR', 'FY2024', null, evFinancialStatements.id, tx.id);

  const fNetWorth = await establishFact(haroon.token, amina.token, wsByType.FINANCIAL_DUE_DILIGENCE,
    'issuer.net_worth', 'Net worth', '482000000', 'INR', 'FY2026', null, evFinancialStatements.id, tx.id);

  const fLicence = await establishFact(haroon.token, amina.token, wsByType.REGULATORY_DUE_DILIGENCE,
    'issuer.licence_expiry', 'Material licence expiry', '1956489600000', 'EPOCH_MS', null, null, evLicences.id, tx.id);

  console.log('  ✓ Grounded and verified 14 core transaction facts.');

  // 7. Realistic Conflict Creation and Resolution
  console.log('\n7. Setting up and resolving realistic promoter holding conflict...');
  let conflicts = await api(tariq.token, 'GET', `/transactions/${tx.id}/conflicts`);
  let openConflict = conflicts.find(c => c.status === 'OPEN' && c.factKey === 'issuer.promoter_ownership_pct');
  const existingConflict = conflicts.find(c => c.factKey === 'issuer.promoter_ownership_pct');

  if (!existingConflict) {
    const evMgt7Conflict = await upload(haroon.token, wsByType.CAPITAL_STRUCTURE,
      'annual-return-mgt7-promoter-holding-pre-bonus.txt',
      'Annual Return MGT-7 as of AGM 2025: Promoter holding recorded at 64.50% based on pre-bonus share capital.',
      'GOVERNMENT_FILING');

    const factA = await api(tariq.token, 'POST', `/workstreams/${wsByType.CAPITAL_STRUCTURE}/facts`, {
      factKey: 'issuer.promoter_ownership_pct',
      label: 'Promoter shareholding percentage',
      value: '64.50',
      unit: '%',
      validFrom: '2026-08-01T00:00:00Z',
    });
    await api(tariq.token, 'POST', `/facts/${factA.id}/evidence-links`, { evidenceId: evMgt7Conflict.id });

    const factB = await api(tariq.token, 'POST', `/workstreams/${wsByType.CAPITAL_STRUCTURE}/facts`, {
      factKey: 'issuer.promoter_ownership_pct',
      label: 'Promoter shareholding percentage',
      value: '61.22',
      unit: '%',
      validFrom: '2026-09-30T00:00:00Z',
    });
    await api(tariq.token, 'POST', `/facts/${factB.id}/evidence-links`, { evidenceId: evBenpos.id });

    conflicts = await api(tariq.token, 'GET', `/transactions/${tx.id}/conflicts`);
    openConflict = conflicts.find(c => c.status === 'OPEN' && c.factKey === 'issuer.promoter_ownership_pct');

    if (openConflict) {
      await api(tariq.token, 'POST', `/conflicts/${openConflict.id}/resolve`, {
        chosenFactId: factB.id,
        reason: 'Auditor Reconciliation Confirmed: 61.22% reflects the post-bonus issue capital structure as filed in Form PAS-3 and confirmed by depository BENPOS as of 30 September 2026. The 64.50% figure in Form MGT-7 predated the bonus issue.',
        evidenceConsideredIds: [evMgt7Conflict.id, evBenpos.id],
      });
      console.log(`  ✓ Conflict resolved with post-bonus verified fact (${factB.id})`);
      try {
        await api(amina.token, 'POST', `/facts/${factB.id}/verify`);
        console.log('  ✓ Chosen fact verified by Statutory Auditor');
      } catch (e) {
        console.warn(`  ! Verify chosen fact: ${e.message}`);
      }
    }
  } else if (openConflict) {
    const txFacts = await api(tariq.token, 'GET', `/transactions/${tx.id}/facts`);
    const factB = txFacts.find(f => f.factKey === 'issuer.promoter_ownership_pct' && f.value === '61.22');
    if (factB) {
      await api(tariq.token, 'POST', `/conflicts/${openConflict.id}/resolve`, {
        chosenFactId: factB.id,
        reason: 'Auditor Reconciliation Confirmed: 61.22% reflects the post-bonus issue capital structure as filed in Form PAS-3 and confirmed by depository BENPOS as of 30 September 2026. The 64.50% figure in Form MGT-7 predated the bonus issue.',
        evidenceConsideredIds: [evBenpos.id],
      });
      console.log(`  ✓ Open conflict resolved with post-bonus verified fact (${factB.id})`);
    }
  } else {
    console.log('  ✓ Conflict already resolved previously.');
  }

  // 8. Diligence Questions
  console.log('\n8. Answering and accepting blocking diligence questions...');
  const questions = await api(tariq.token, 'GET', `/transactions/${tx.id}/diligence-questions`);
  console.log(`  Total diligence questions: ${questions.length}`);

  const answersMap = {
    CAPITAL_STRUCTURE: {
      text: 'Verified against NSDL/CDSL BENPOS reports and Register of Members (Form MGT-1). Pre-issue paid-up capital is INR 24.50 Cr and post-issue capital will be INR 28.70 Cr. All promoter shares are fully dematerialized.',
      evId: evRegisterMembers.id,
    },
    FINANCIAL_DUE_DILIGENCE: {
      text: 'Restated standalone financials for FY24, FY25, and FY26 audited by Al-Qasim & Partners. Peer review certificate valid through 2027. Unmodified examination report issued under Part IX of SEBI ICDR.',
      evId: evFinancialStatements.id,
    },
    LEGAL_DUE_DILIGENCE: {
      text: 'Legal Due Diligence Report executed by Al-Khatib & Associates confirms clean corporate title, valid IP trademarks, and no pending winding up or insolvency petitions against company or promoters.',
      evId: evLegalDiligence.id,
    },
    REGULATORY_DUE_DILIGENCE: {
      text: 'Factory licence valid until 31 Dec 2031 under DISH. Environmental consents in place under MPCB. RBI and FEMA export-import certificates verified with authorized dealer bank.',
      evId: evLicences.id,
    },
  };

  for (const q of questions) {
    if (q.status === 'OPEN') {
      const ansInfo = answersMap[q.workstreamType] || {
        text: 'Documented, verified with management, and confirmed against statutory filings in the linked evidence file.',
        evId: evBoardRes.id,
      };
      try {
        await api(tariq.token, 'PUT', `/diligence-questions/${q.id}/answer`, {
          answer: ansInfo.text,
          evidenceIds: [ansInfo.evId],
          factIds: [fPostCapital.id],
        });
        // Second person accepts answer
        await api(amina.token, 'POST', `/diligence-questions/${q.id}/accept`);
      } catch (e) {
        // ignore if already answered
      }
    }
  }
  console.log('  ✓ All diligence questions answered and accepted by Syndicate leads.');

  // 9. Populate Kanban Task Board
  console.log('\n9. Seeding collaborative Kanban tasks across all 16 members...');
  const existingTasks = await api(haroon.token, 'GET', `/transactions/${tx.id}/tasks`);
  const existingTitles = new Set(existingTasks.map(t => t.title));
  const tasksDef = [
      // RESOLVED
      {
        title: 'Drafting of Restated Standalone Financial Statements (FY24-FY26)',
        desc: 'Compile restated financial tables, Ind AS reconciliation, and note disclosures for the last 3 financial years.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'BLOCKING',
        user: users['amina'],
        status: 'RESOLVED',
        resNote: 'Restated financials finalized in accordance with SEBI ICDR Part IX; unmodified audit opinion issued.',
        due: '2026-09-15',
      },
      {
        title: 'Secretarial Audit & ROC Filings Reconciliation',
        desc: 'Reconcile all MCA filings (Forms MGT-7, AOC-4, PAS-3) with physical secretarial records.',
        ws: wsByType.CORPORATE_SECRETARIAL,
        sev: 'HIGH',
        user: users['zaid'],
        status: 'RESOLVED',
        resNote: 'All statutory registers updated and reconciled with MCA portal. No pending notices.',
        due: '2026-09-18',
      },
      {
        title: 'Material Contracts Materiality Assessment (5% Revenue Threshold)',
        desc: 'Review tier-1 customer agreements and prepare summary annexures for DRHP disclosure.',
        ws: wsByType.MATERIAL_CONTRACTS,
        sev: 'HIGH',
        user: users['layla'],
        status: 'RESOLVED',
        resNote: 'Reviewed 14 tier-1 customer agreements; summaries incorporated into DRHP Annexure III.',
        due: '2026-09-22',
      },
      {
        title: 'Direct & Indirect Tax Clearance Certificate Collation',
        desc: 'Obtain assessment orders under Sec 143(3) and Nil TDS certificates under Sec 197 for AY24-AY26.',
        ws: wsByType.TAX,
        sev: 'MEDIUM',
        user: users['bilal'],
        status: 'RESOLVED',
        resNote: 'Assessment orders and Nil TDS certificates received and uploaded to Tax workstream.',
        due: '2026-09-25',
      },

      // IN_REVIEW
      {
        title: 'Depository Tripartite Agreement (NSDL & CDSL ISIN Activation)',
        desc: 'Execute tripartite agreements between Issuer, Registrar, and Depositories for electronic credit of IPO shares.',
        ws: wsByType.CAPITAL_STRUCTURE,
        sev: 'HIGH',
        user: users['ibrahim'],
        status: 'IN_REVIEW',
        due: '2026-10-15',
      },
      {
        title: 'Market Maker Inventory & Underwriting Agreement Finalization',
        desc: 'Finalize market making commitment of minimum 5% issue size and 3-year market making contract.',
        ws: wsByType.CAPITAL_STRUCTURE,
        sev: 'HIGH',
        user: users['samira'],
        status: 'IN_REVIEW',
        due: '2026-10-18',
      },
      {
        title: 'Promoter Lock-in & Demat Undertaking Notarization',
        desc: 'Obtain notarized undertakings from Omar Al-Farooq for 20% minimum promoter contribution lock-in for 3 years.',
        ws: wsByType.CAPITAL_STRUCTURE,
        sev: 'BLOCKING',
        user: users['omar'],
        status: 'IN_REVIEW',
        due: '2026-10-16',
      },
      {
        title: 'Restated Peer Review Auditor Certificate Verification',
        desc: 'Verify that statutory audit firm holds a valid Peer Review Certificate issued by ICAI Peer Review Board.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'HIGH',
        user: users['noor'],
        status: 'IN_REVIEW',
        due: '2026-10-14',
      },

      // IN_PROGRESS
      {
        title: 'Drafting DRHP Risk Factors & Basis for Issue Price Section',
        desc: 'Draft internal and external risk factors, P/E comparison against listed industry peers, and NAV basis.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'HIGH',
        user: users['tariq'],
        status: 'IN_PROGRESS',
        due: '2026-10-20',
      },
      {
        title: 'Vendor & Customer Due Diligence Confirmation Calls',
        desc: 'Conduct independent due diligence calls with top 5 customers and top 5 suppliers representing >40% revenue.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'MEDIUM',
        user: users['hamza'],
        status: 'IN_PROGRESS',
        due: '2026-10-22',
      },
      {
        title: 'Drafting Corporate Governance & Management Structure Section',
        desc: 'Prepare profiles of Independent Directors, Board Committees (Audit, Nomination & Remuneration), and KMP disclosures.',
        ws: wsByType.LEGAL_DUE_DILIGENCE,
        sev: 'MEDIUM',
        user: users['youssef'],
        status: 'IN_PROGRESS',
        due: '2026-10-21',
      },
      {
        title: 'Equity Syndication & Institutional Investor Roadshow Deck',
        desc: 'Prepare confidential investor presentation for anchor investors and QIB high-net-worth individuals.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'MEDIUM',
        user: users['mariam'],
        status: 'IN_PROGRESS',
        due: '2026-10-25',
      },
      {
        title: 'Review of Key Litigations and Regulatory Disclosures',
        desc: 'Audit all pending show-cause notices or civil disputes involving Trinity Capital or promoter entities.',
        ws: wsByType.LITIGATION,
        sev: 'HIGH',
        user: users['khaled'],
        status: 'IN_PROGRESS',
        due: '2026-10-19',
      },

      // TODO
      {
        title: 'Bank Escrow and Sponsor Bank Agreement Execution',
        desc: 'Draft and execute Escrow Agreement with Sponsor Bank and Public Issue Account Banker.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'HIGH',
        user: users['fatima'],
        status: 'TODO',
        due: '2026-10-28',
      },
      {
        title: 'Registrar Bid-cum-Application Form Template Sign-off',
        desc: 'Review and approve ASBA / UPI application form layout for retail and non-institutional bidders.',
        ws: wsByType.CAPITAL_STRUCTURE,
        sev: 'MEDIUM',
        user: users['ibrahim'],
        status: 'TODO',
        due: '2026-10-27',
      },
      {
        title: 'SEBI & Exchange Final Observation Compliance Undertaking',
        desc: 'Compile point-by-point compliance undertaking addressing all regulatory clarification letters.',
        ws: wsByType.REGULATORY_DUE_DILIGENCE,
        sev: 'HIGH',
        user: users['mustafa'],
        status: 'TODO',
        due: '2026-10-30',
      },
      {
        title: 'Statutory Auditor Comfort Letter on Cut-Off Date',
        desc: 'Issue negative assurance comfort letter to Lead Merchant Banker covering period up to filing date.',
        ws: wsByType.FINANCIAL_DUE_DILIGENCE,
        sev: 'HIGH',
        user: users['amina'],
        status: 'TODO',
        due: '2026-11-02',
      },
      {
        title: 'Board Meeting Resolution for RHP Approval & Price Band Fixation',
        desc: 'Convene Board of Directors meeting to adopt Red Herring Prospectus and authorize IPO opening dates.',
        ws: wsByType.CORPORATE_SECRETARIAL,
        sev: 'BLOCKING',
        user: users['haroon'],
        status: 'TODO',
        due: '2026-11-05',
      },
    ];

    for (const td of tasksDef) {
      if (existingTitles.has(td.title)) {
        continue;
      }
      const created = await api(haroon.token, 'POST', `/transactions/${tx.id}/tasks`, {
        title: td.title,
        description: td.desc,
        workstreamId: td.ws,
        severity: td.sev,
        dueDate: td.due,
      });

      await api(haroon.token, 'PATCH', `/tasks/${created.id}`, {
        assignedUserId: td.user.user.id,
        status: td.status,
        resolutionNote: td.resNote || undefined,
      });
      console.log(`  ✓ Task created & routed: [${td.status}] ${td.title.slice(0, 45)}... -> ${td.user.fullName}`);
    }

  // 10. DRHP Disclosures & Compilation
  console.log('\n10. Writing DRHP disclosures & compiling final filing copy...');
  const existingDisclosures = await api(haroon.token, 'GET', `/transactions/${tx.id}/disclosures`);
  let disc1 = existingDisclosures.find(d => d.sectionCode === 'CAPITAL_STRUCTURE');
  if (!disc1) {
    disc1 = await api(haroon.token, 'POST', `/transactions/${tx.id}/disclosures`, {
      sectionCode: 'CAPITAL_STRUCTURE',
      title: 'Capital Structure of Trinity Capital Limited',
      bodyTemplate: 'The authorized share capital of Trinity Capital Limited is INR 35,00,00,000 divided into 3,50,00,000 equity shares of face value INR 10 each. The post-issue paid-up capital of the Company is INR 28,70,00,000 comprising 2,87,00,000 equity shares of INR 10 each. The Fresh Issue consists of 42,00,000 equity shares and the Offer for Sale consists of 18,00,000 equity shares offered by Promoter Omar Al-Farooq. Promoters hold 61.22% pre-issue and will maintain minimum promoter contribution of 20% locked in for three years.',
      orderIndex: 1,
    });
    await api(haroon.token, 'POST', `/disclosures/${disc1.id}/fact-links`, { factId: fPostCapital.id });
    await api(haroon.token, 'POST', `/disclosures/${disc1.id}/fact-links`, { factId: fFreshIssue.id });
    console.log(`  ✓ Created Capital Structure disclosure (${disc1.id})`);
  }

  let disc2 = existingDisclosures.find(d => d.sectionCode === 'FINANCIAL_INFORMATION');
  if (!disc2) {
    disc2 = await api(haroon.token, 'POST', `/transactions/${tx.id}/disclosures`, {
      sectionCode: 'FINANCIAL_INFORMATION',
      title: 'Summary Financial Information',
      bodyTemplate: 'The summary restated standalone financial information of Trinity Capital Limited for the financial years ended 31 March 2026, 31 March 2025, and 31 March 2024 shows Total Revenue of INR 82,45,00,000 (FY26), INR 68,20,00,000 (FY25), and INR 54,10,00,000 (FY24). Restated EBITDA for FY26 stood at INR 16,80,00,000 and Restated PAT for FY26 was INR 11,40,00,000. All figures have been examined and certified by Statutory Auditor Al-Qasim & Partners.',
      orderIndex: 2,
    });
    await api(haroon.token, 'POST', `/disclosures/${disc2.id}/fact-links`, { factId: fRev26.id });
    await api(haroon.token, 'POST', `/disclosures/${disc2.id}/fact-links`, { factId: fEbitda26.id });
    await api(haroon.token, 'POST', `/disclosures/${disc2.id}/fact-links`, { factId: fPat26.id });
    console.log(`  ✓ Created Financial Information disclosure (${disc2.id})`);
  }

  let disc3 = existingDisclosures.find(d => d.sectionCode === 'OBJECTS_OF_THE_ISSUE');
  if (!disc3) {
    disc3 = await api(haroon.token, 'POST', `/transactions/${tx.id}/disclosures`, {
      sectionCode: 'OBJECTS_OF_THE_ISSUE',
      title: 'Objects of the Fresh Issue and OFS',
      bodyTemplate: 'The net proceeds from the Fresh Issue of INR 52,50,00,000 (at floor price) will be deployed towards: 1) Expansion of Enterprise Fintech Infrastructure and Data Centers (INR 24,00,00,000), 2) Working Capital Requirements (INR 18,50,00,000), and 3) General Corporate Purposes (INR 10,00,00,000). The proceeds from Offer for Sale will be remitted directly to the Selling Shareholder.',
      orderIndex: 3,
    });
    console.log(`  ✓ Created Objects of the Issue disclosure (${disc3.id})`);
  }

  // Evaluate readiness rules
  await api(haroon.token, 'POST', `/transactions/${tx.id}/readiness/evaluate`);

  // Compile DRHP with FINAL_FILING mode
  console.log('\n11. Compiling DRHP with Merkle Provenance Manifest...');
  const compileRes = await api(haroon.token, 'POST', `/transactions/${tx.id}/drhp/compile?mode=FINAL_FILING`);
  const doc = compileRes.document;
  if (doc) {
    console.log(`  ✓ DRHP v${doc.version} compiled! Merkle root: ${doc.merkleRoot ? doc.merkleRoot.slice(0, 16) + '...' : 'none'}`);
  } else {
    console.log(`  ! Compile preview:`, compileRes.findings || compileRes.preview);
  }

  // Approvals for compiled DRHP
  const latestDrhp = await api(haroon.token, 'GET', `/transactions/${tx.id}/drhp`);
  console.log('\n12. Signing approvals as Issuer Admin and Lead Banker...');
  if (latestDrhp && latestDrhp.id) {
    try {
      await api(haroon.token, 'POST', `/drhp/versions/${latestDrhp.id}/approvals`);
      console.log('  ✓ Approved by Haroon Al-Mansoor (ISSUER_ADMIN)');
    } catch (e) {
      console.log(`  - Haroon approval: ${e.message}`);
    }

    try {
      await api(tariq.token, 'POST', `/drhp/versions/${latestDrhp.id}/approvals`);
      console.log('  ✓ Approved by Tariq Al-Hashimi (LEAD_BANKER)');
    } catch (e) {
      console.log(`  - Tariq approval: ${e.message}`);
    }
  }

  // Assemble filing package
  console.log('\n13. Assembling cryptographic filing package...');
  try {
    const filingStatus = await api(haroon.token, 'POST', `/drhp/versions/${latestDrhp.id}/filing-package`);
    console.log(`  ✓ Filing package assembled! Package ID: ${filingStatus.package?.id}`);
  } catch (e) {
    console.log(`  - Package assemble note: ${e.message}`);
  }

  // 14. Regulatory Observation & Response
  console.log('\n14. Recording SEBI / BSE SME Observation with grounded response...');
  const existingObs = await api(haroon.token, 'GET', `/transactions/${tx.id}/observations`);
  let obs = existingObs.find(o => o.reference?.includes('SEBI/CFD'));
  if (!obs) {
    obs = await api(haroon.token, 'POST', `/transactions/${tx.id}/observations`, {
      authority: 'SEBI / BSE SME',
      reference: 'SEBI/CFD/DCR/OBS/2026/09/142',
      receivedDate: '2026-09-28',
      observation: 'With reference to the Draft Red Herring Prospectus of Trinity Capital Limited, clarify the reconciliation between promoter shareholding reported in Form MGT-7 (64.50%) and the pre-issue capital schedule (61.22%). Confirm compliance with Regulation 252 regarding minimum promoter contribution lock-in commitments.',
      sectionCode: 'CAPITAL_STRUCTURE',
      severity: 'HIGH',
      responseDeadline: '2026-10-25',
      ownerUserId: tariq.user.id,
      factIds: [fPostCapital.id],
      evidenceIds: [evRegisterMembers.id, evBenpos.id],
      disclosureIds: [disc1.id],
    });
    console.log(`  ✓ Recorded Regulatory Observation: ${obs.id}`);

    // Draft response by Tariq
    await api(tariq.token, 'PUT', `/observations/${obs.id}/response`, {
      response: 'The variance of 3.28% between Form MGT-7 and the pre-issue capital schedule is attributable to the 1:5 bonus issue allotment executed on 15 September 2026 pursuant to EGM shareholder resolution, which expanded equity capital from 20,416,666 shares to 24,500,000 shares. Minimum promoter contribution of 20% post-issue capital (comprising 5,740,000 equity shares) has been earmarked for 3-year statutory lock-in, with balance promoter shares locked-in for 1 year in full compliance with SEBI (ICDR) Regulation 252. Physical and depository demat holdings have been reconciled with NSDL and CDSL.',
      factIds: [fPostCapital.id],
      evidenceIds: [evBenpos.id, evRegisterMembers.id],
      disclosureIds: [disc1.id],
    });
    console.log('  ✓ Response drafted by Tariq Al-Hashimi (Lead Banker)');

    // Approve response by Amina Al-Qasim (Statutory Auditor / 4-eyes principle)
    await api(amina.token, 'POST', `/observations/${obs.id}/response/approve`);
    console.log('  ✓ Response approved by Amina Al-Qasim (Statutory Auditor)');
  } else {
    console.log(`  - Observation already present: ${obs.id}`);
  }

  // 15. Record Milestone
  console.log('\n15. Recording filing milestone...');
  try {
    await api(haroon.token, 'POST', `/transactions/${tx.id}/milestones/FILED`, {
      occurredOn: '2026-09-20',
      reference: 'BSE/SME/IPO/2026/TRINITY-01',
      notes: 'Initial Draft Red Herring Prospectus (DRHP) officially filed with BSE SME Platform for in-principle approval.',
    });
    console.log('  ✓ Recorded FILED milestone');
  } catch (e) {
    console.log(`  - Milestone note: ${e.message}`);
  }

  console.log('\n======================================================');
  console.log('✓ SEEDING COMPLETED SUCCESSFULLY!');
  console.log('  Transaction: Trinity Capital Limited SME IPO');
  console.log('  Primary User: haroon@trinitycapital.com');
  console.log('  Password:     haroon@trinity');
  console.log('  Collaborators: 15 active members with Arabic names across Issuer, Merchant Banker, Legal Counsel, Auditor, Tax, Registrar, Market Maker, and Regulatory Consultant.');
  console.log('======================================================');
}

main().catch(err => {
  console.error('SEED SCRIPT FAILED:', err);
  process.exit(1);
});
