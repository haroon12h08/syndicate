import fs from 'fs';
import path from 'path';

async function main() {
  const { chromium } = await import('./frontend/node_modules/playwright-core/index.mjs');

  const OUT_DIR = '/home/haroon/.gemini/antigravity-cli/brain/5ad826dd-2b4f-437b-bebc-9f909116eae6';
  const PUBLIC_DIR = '/home/haroon/Desktop/syndicate/screenshots';
  const DOCS_DIR = '/home/haroon/Desktop/syndicate/docs/assets/figures';
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
  if (!fs.existsSync(PUBLIC_DIR)) fs.mkdirSync(PUBLIC_DIR, { recursive: true });
  if (!fs.existsSync(DOCS_DIR)) fs.mkdirSync(DOCS_DIR, { recursive: true });

  const browser = await chromium.launch({
    headless: true,
    executablePath: '/usr/bin/google-chrome-stable',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  const page = await context.newPage();

  console.log('1. Navigating to login...');
  await page.goto('http://localhost:8080/login', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="email"]');

  console.log('2. Logging in as Haroon (Issuer Admin)...');
  await page.fill('input[type="email"]', 'haroon@trinitycapital.com');
  await page.fill('input[type="password"]', 'haroon@trinity');
  await page.click('button[type="submit"]');

  await page.waitForURL('**/home', { timeout: 10000 });
  await page.waitForSelector('.app-sidebar');

  const txId = 'bb5c6982-51ea-4f00-a6e0-f43aef6cdf09';
  console.log('3. Navigating to Trinity Capital Transaction Detail...');
  await page.goto(`http://localhost:8080/transactions/${txId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.tab-strip');
  await page.waitForTimeout(1000);

  // Capture 1: Main Regulatory Workbench
  console.log('4. Opening Regulatory Engine Tab...');
  await page.click('button:has-text("Regulatory Engine")');
  await page.waitForSelector('.regulatory-workbench');
  await page.waitForTimeout(1500);

  const mainBrain = path.join(OUT_DIR, 'phase-d-regulatory-workbench.png');
  const mainPublic = path.join(PUBLIC_DIR, 'phase-d-regulatory-workbench.png');
  const mainDocs = path.join(DOCS_DIR, 'phase-d-regulatory-workbench.png');
  await page.screenshot({ path: mainBrain });
  fs.copyFileSync(mainBrain, mainPublic);
  fs.copyFileSync(mainBrain, mainDocs);
  console.log('✓ Captured phase-d-regulatory-workbench.png');

  // Capture 2: Inspect Inputs Modal
  console.log('5. Opening Control Inputs Modal...');
  const inspectBtn = page.locator('button:has-text("Inspect Inputs")').first();
  if (await inspectBtn.isVisible()) {
    await inspectBtn.click();
    await page.waitForSelector('.modal-card');
    await page.waitForTimeout(800);
    const modalBrain = path.join(OUT_DIR, 'phase-d-control-inspection.png');
    const modalPublic = path.join(PUBLIC_DIR, 'phase-d-control-inspection.png');
    const modalDocs = path.join(DOCS_DIR, 'phase-d-control-inspection.png');
    await page.screenshot({ path: modalBrain });
    fs.copyFileSync(modalBrain, modalPublic);
    fs.copyFileSync(modalBrain, modalDocs);
    console.log('✓ Captured phase-d-control-inspection.png');
    // Close modal
    await page.click('button:has-text("Close")');
    await page.waitForTimeout(500);
  }

  // Capture 2b: Request Exception Modal
  console.log('5b. Opening Request Exception Modal...');
  const excBtn = page.locator('button:has-text("Request Exception")').first();
  if (await excBtn.isVisible()) {
    await excBtn.click();
    await page.waitForSelector('.modal-card');
    await page.waitForTimeout(800);
    const excModalBrain = path.join(OUT_DIR, 'phase-d-exception-modal.png');
    const excModalPublic = path.join(PUBLIC_DIR, 'phase-d-exception-modal.png');
    const excModalDocs = path.join(DOCS_DIR, 'phase-d-exception-modal.png');
    await page.screenshot({ path: excModalBrain });
    fs.copyFileSync(excModalBrain, excModalPublic);
    fs.copyFileSync(excModalBrain, excModalDocs);
    console.log('✓ Captured phase-d-exception-modal.png');
    await page.click('button:has-text("Cancel")');
    await page.waitForTimeout(500);
  }

  // Capture 3: Rule Governance & Testing Sub-tab
  console.log('6. Switching to Rule Governance & Testing Sub-tab...');
  await page.click('button:has-text("Rule Governance & Testing")');
  await page.waitForTimeout(1200);

  const govBrain = path.join(OUT_DIR, 'phase-d-governance-center.png');
  const govPublic = path.join(PUBLIC_DIR, 'phase-d-governance-center.png');
  const govDocs = path.join(DOCS_DIR, 'phase-d-governance-center.png');
  await page.screenshot({ path: govBrain });
  fs.copyFileSync(govBrain, govPublic);
  fs.copyFileSync(govBrain, govDocs);
  console.log('✓ Captured phase-d-governance-center.png');

  // Capture 4: State Machine Transition Modal
  console.log('7. Opening State Machine Transition Modal for DUE_DILIGENCE...');
  const stageStep = page.locator('.lifecycle-step:has-text("DUE DILIGENCE")');
  if (await stageStep.isVisible()) {
    await stageStep.click();
    await page.waitForSelector('.modal-card');
    await page.waitForTimeout(1000);

    const transBrain = path.join(OUT_DIR, 'phase-d-transition-modal.png');
    const transPublic = path.join(PUBLIC_DIR, 'phase-d-transition-modal.png');
    const transDocs = path.join(DOCS_DIR, 'phase-d-transition-modal.png');
    await page.screenshot({ path: transBrain });
    fs.copyFileSync(transBrain, transPublic);
    fs.copyFileSync(transBrain, transDocs);
    console.log('✓ Captured phase-d-transition-modal.png');

    await page.click('button:has-text("Cancel")');
    await page.waitForTimeout(500);
  }

  await browser.close();
  console.log('All Phase D Regulatory Engine screenshots captured successfully!');
}

main().catch((err) => {
  console.error('Screenshot capture failed:', err);
  process.exit(1);
});
