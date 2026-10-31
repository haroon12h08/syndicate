import fs from 'fs';
import path from 'path';

async function main() {
  const { chromium } = await import('./frontend/node_modules/playwright-core/index.mjs');

  const OUT_DIR = '/home/haroon/.gemini/antigravity-cli/brain/5ad826dd-2b4f-437b-bebc-9f909116eae6';
  const PUBLIC_DIR = '/home/haroon/Desktop/syndicate/screenshots';
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
  if (!fs.existsSync(PUBLIC_DIR)) fs.mkdirSync(PUBLIC_DIR, { recursive: true });

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

  // Capture 1: Cap Table Workbench
  console.log('4. Opening Cap Table Tab...');
  await page.click('button:has-text("Cap Table")');
  await page.waitForSelector('.captable-workbench');
  await page.waitForTimeout(1500);

  const capTableBrain = path.join(OUT_DIR, 'phase-c-captable-workbench.png');
  const capTablePublic = path.join(PUBLIC_DIR, 'phase-c-captable-workbench.png');
  await page.screenshot({ path: capTableBrain });
  fs.copyFileSync(capTableBrain, capTablePublic);
  console.log('✓ Captured phase-c-captable-workbench.png');

  // Sub-tab: SEBI Lock-In Schedule
  console.log('5. Viewing SEBI 2025 Lock-In Schedule...');
  await page.click('button:has-text("SEBI Lock-In Schedule")');
  await page.waitForTimeout(1000);
  const lockInBrain = path.join(OUT_DIR, 'phase-c-lockin-schedule.png');
  const lockInPublic = path.join(PUBLIC_DIR, 'phase-c-lockin-schedule.png');
  await page.screenshot({ path: lockInBrain });
  fs.copyFileSync(lockInBrain, lockInPublic);
  console.log('✓ Captured phase-c-lockin-schedule.png');

  // Capture 2: Registers Panel - RPT
  console.log('6. Opening Registers Tab (RPT)...');
  await page.click('button:has-text("Registers")');
  await page.waitForSelector('.registers-panel');
  await page.waitForTimeout(1000);

  const rptBrain = path.join(OUT_DIR, 'phase-c-registers-rpt.png');
  const rptPublic = path.join(PUBLIC_DIR, 'phase-c-registers-rpt.png');
  await page.screenshot({ path: rptBrain });
  fs.copyFileSync(rptBrain, rptPublic);
  console.log('✓ Captured phase-c-registers-rpt.png');

  // Capture 3: Registers Panel - Litigation
  console.log('7. Switching to Litigation Sub-tab...');
  await page.click('button:has-text("Litigation & Disputes")');
  await page.waitForTimeout(1000);

  const litBrain = path.join(OUT_DIR, 'phase-c-registers-litigation.png');
  const litPublic = path.join(PUBLIC_DIR, 'phase-c-registers-litigation.png');
  await page.screenshot({ path: litBrain });
  fs.copyFileSync(litBrain, litPublic);
  console.log('✓ Captured phase-c-registers-litigation.png');

  // Capture 4: Registers Panel - Material Contracts
  console.log('8. Switching to Material Commercial Contracts Sub-tab...');
  await page.click('button:has-text("Material Commercial Contracts")');
  await page.waitForTimeout(1000);

  const contractBrain = path.join(OUT_DIR, 'phase-c-registers-contracts.png');
  const contractPublic = path.join(PUBLIC_DIR, 'phase-c-registers-contracts.png');
  await page.screenshot({ path: contractBrain });
  fs.copyFileSync(contractBrain, contractPublic);
  console.log('✓ Captured phase-c-registers-contracts.png');

  await browser.close();
  console.log('All Phase C domain depth screenshots captured successfully!');
}

main().catch((err) => {
  console.error('Screenshot capture failed:', err);
  process.exit(1);
});
