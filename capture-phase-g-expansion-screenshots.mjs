import fs from 'fs';
import path from 'path';

async function main() {
  const { chromium } = await import('./frontend/node_modules/playwright-core/index.mjs');

  const BRAIN_DIR = '/home/haroon/.gemini/antigravity-cli/brain/5ad826dd-2b4f-437b-bebc-9f909116eae6';
  const PUBLIC_DIR = '/home/haroon/Desktop/syndicate/screenshots';
  const FIGURES_DIR = '/home/haroon/Desktop/syndicate/docs/assets/figures';

  for (const dir of [BRAIN_DIR, PUBLIC_DIR, FIGURES_DIR]) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  }

  function saveScreenshot(tempPath, fileName) {
    const p1 = path.join(BRAIN_DIR, fileName);
    const p2 = path.join(PUBLIC_DIR, fileName);
    const p3 = path.join(FIGURES_DIR, fileName);
    fs.copyFileSync(tempPath, p1);
    fs.copyFileSync(tempPath, p2);
    fs.copyFileSync(tempPath, p3);
    console.log(`✓ Saved ${fileName} to brain, screenshots, and docs/assets/figures`);
  }

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

  console.log('2. Logging in as Haroon...');
  await page.fill('input[type="email"]', 'haroon@trinitycapital.com');
  await page.fill('input[type="password"]', 'haroon@trinity');
  await page.click('button[type="submit"]');

  await page.waitForURL('**/home', { timeout: 10000 });
  await page.waitForSelector('.app-sidebar');

  const txId = 'bb5c6982-51ea-4f00-a6e0-f43aef6cdf09';
  console.log('3. Navigating to Trinity Transaction Detail...');
  await page.goto(`http://localhost:8080/transactions/${txId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('h1');
  await page.waitForTimeout(1000);

  // --------------------------------------------------------------------------
  // Figure 14: Unified Transaction Search Modal
  // --------------------------------------------------------------------------
  console.log('4. Capturing Figure 14: Unified Transaction Search Modal...');
  const searchBtn = page.locator('button:has-text("🔍 Search")').first();
  await searchBtn.click();
  await page.waitForSelector('.palette-backdrop', { state: 'visible' });
  await page.waitForTimeout(400);

  const searchInput = page.locator('.palette input').first();
  await searchInput.fill('Capital');
  await page.waitForTimeout(1200);

  const fig14Temp = '/tmp/fig14-transaction-search.png';
  await page.screenshot({ path: fig14Temp });
  saveScreenshot(fig14Temp, 'fig14-transaction-search.png');

  // Close search modal
  await page.keyboard.press('Escape');
  await page.waitForSelector('.palette-backdrop', { state: 'detached' });
  await page.waitForTimeout(500);

  // --------------------------------------------------------------------------
  // Figure 15: AI Suggestions Panel
  // --------------------------------------------------------------------------
  console.log('5. Capturing Figure 15: AI Suggestions Panel...');
  const suggestionsTab = page.locator('button:has-text("AI Suggestions")').first();
  await suggestionsTab.click();
  await page.waitForSelector('.ai-suggestions-panel', { state: 'visible' });
  await page.waitForTimeout(1200);

  const fig15Temp = '/tmp/fig15-ai-suggestions-panel.png';
  await page.screenshot({ path: fig15Temp });
  saveScreenshot(fig15Temp, 'fig15-ai-suggestions-panel.png');

  // --------------------------------------------------------------------------
  // Figure 16: DRHP Disclosure AI Draft Assist
  // --------------------------------------------------------------------------
  console.log('6. Capturing Figure 16: DRHP Disclosure AI Draft Assist...');
  const drhpTab = page.locator('button:has-text("DRHP")').first();
  await drhpTab.click();
  await page.waitForSelector('table.data-table', { state: 'visible' });
  await page.waitForTimeout(800);

  // Click edit on the first disclosure
  const editBtn = page.locator('table.data-table tbody tr button:has-text("Edit")').first();
  await editBtn.click();
  await page.waitForSelector('.drawer', { state: 'visible' });
  await page.waitForTimeout(600);

  // Click AI Draft Assist
  const aiDraftBtn = page.locator('button:has-text("✨ AI Draft Assist")').first();
  await aiDraftBtn.click();
  await page.waitForSelector('.palette', { state: 'visible' });
  await page.waitForTimeout(500);

  // Click generate
  const genDraftBtn = page.locator('button:has-text("Generate Draft Prose")').first();
  await genDraftBtn.click();
  await page.waitForSelector('text=Generated Draft Preview', { timeout: 10000 });
  await page.waitForTimeout(1000);

  const fig16Temp = '/tmp/fig16-ai-disclosure-draft-assist.png';
  await page.screenshot({ path: fig16Temp });
  saveScreenshot(fig16Temp, 'fig16-ai-disclosure-draft-assist.png');

  // Close AI modal and drawer
  const closeAiModalBtn = page.locator('.palette button:has-text("✕")').first();
  await closeAiModalBtn.click();
  await page.waitForTimeout(400);
  await page.locator('.drawer-backdrop').click({ position: { x: 50, y: 50 } });
  await page.waitForTimeout(500);

  // --------------------------------------------------------------------------
  // Figure 17: Regulatory Observation AI Response Assist
  // --------------------------------------------------------------------------
  console.log('7. Capturing Figure 17: Regulatory Observation AI Response Assist...');
  const obsTab = page.locator('button:has-text("Observations")').first();
  await obsTab.click();
  await page.waitForSelector('.card-list', { state: 'visible' });
  await page.waitForTimeout(800);

  // Find the open observation card and click Write response (or Revise response)
  const writeRespBtn = page.locator('button:has-text("Write response"), button:has-text("Revise response")').first();
  await writeRespBtn.click();
  await page.waitForSelector('.card-form', { state: 'visible' });
  await page.waitForTimeout(500);

  // Click AI Draft Response
  const aiObsBtn = page.locator('button:has-text("✨ AI Draft Response")').first();
  await aiObsBtn.click();
  // Wait for AI provenance button to appear indicating successful draft generation and linking
  await page.waitForSelector('button:has-text("🔍 AI Provenance")', { timeout: 10000 });
  await page.waitForTimeout(1000);

  const fig17Temp = '/tmp/fig17-ai-observation-response-assist.png';
  await page.screenshot({ path: fig17Temp });
  saveScreenshot(fig17Temp, 'fig17-ai-observation-response-assist.png');

  await browser.close();
  console.log('\nAll Phase G Expansion verification screenshots captured and copied successfully!');
}

main().catch(err => {
  console.error('Screenshot capture failed:', err);
  process.exit(1);
});
