import fs from 'fs';
import path from 'path';

async function main() {
  const { chromium } = await import('./frontend/node_modules/playwright-core/index.mjs');

  const OUT_DIR = '/home/haroon/.gemini/antigravity-cli/brain/5ad826dd-2b4f-437b-bebc-9f909116eae6';
  if (!fs.existsSync(OUT_DIR)) {
    fs.mkdirSync(OUT_DIR, { recursive: true });
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

  console.log('1. Navigating to Login Page...');
  await page.goto('http://localhost:8080/login', { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('input[type="email"]');

  console.log('2. Logging in as Haroon (Issuer Admin)...');
  await page.fill('input[type="email"]', 'haroon@trinitycapital.com');
  await page.fill('input[type="password"]', 'haroon@trinity');
  await page.click('button[type="submit"]');

  // Wait for login redirection
  await page.waitForURL('**/home', { timeout: 10000 });
  await page.waitForSelector('.app-sidebar');
  console.log('✓ Logged in! Current URL:', page.url());

  // Navigate directly to Financial Due Diligence workstream
  console.log('3. Navigating to Financial Due Diligence Workstream...');
  const finWsId = '8939f57f-3cfe-4d9c-8aa0-4fe12682de2f';
  await page.goto(`http://localhost:8080/workstreams/${finWsId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.data-table');
  await page.waitForTimeout(1000);

  // Screenshot 1: Workstream details showing "Grounding" and "View & Grounding" buttons
  const wsScreenshot = path.join(OUT_DIR, 'spatial-evidence-workstream-grounding.png');
  await page.screenshot({ path: wsScreenshot });
  console.log('✓ Captured:', wsScreenshot);

  // Click "View & Grounding" button on the Audited Financial Report
  console.log('4. Opening Spatial Evidence Viewer on trinity-audited-financial-report-fy26.pdf...');
  const pdfRow = page.locator('tr', { hasText: 'trinity-audited-financial-report-fy26.pdf' });
  const viewerBtn = pdfRow.locator('button', { hasText: 'View & Grounding' }).first();
  await viewerBtn.click();

  // Wait for viewer modal and rendered page
  await page.waitForSelector('.spatial-viewer-modal', { state: 'visible' });
  await page.waitForSelector('.spatial-page-img', { state: 'visible' });
  await page.waitForTimeout(1200);

  // Switch to Page 3 which has both Verified and Candidate bounding boxes
  console.log('5. Navigating to Page 3...');
  const pageSelect = page.locator('.page-select');
  await pageSelect.selectOption('3');
  await page.waitForTimeout(1500);

  // Screenshot 2: Multi-page document viewer showing Verified (green) & Candidate (amber) boxes
  const viewerMultipageScreenshot = path.join(OUT_DIR, 'spatial-evidence-viewer-multipage.png');
  await page.screenshot({ path: viewerMultipageScreenshot });
  console.log('✓ Captured:', viewerMultipageScreenshot);

  // Hover over one of the candidate bounding boxes to trigger popover tooltip and click it
  console.log('6. Selecting Candidate Bounding Box for EBITDA / Net Profit...');
  const candidateBox = page.locator('.spatial-bbox.pending').first();
  await candidateBox.hover();
  await page.waitForTimeout(500);
  await candidateBox.click();
  await page.waitForTimeout(800);

  // Screenshot 3: Hover popover and Candidate Fact Review form in Sidebar
  const reviewScreenshot = path.join(OUT_DIR, 'spatial-evidence-viewer-candidate-review.png');
  await page.screenshot({ path: reviewScreenshot });
  console.log('✓ Captured:', reviewScreenshot);

  // Close viewer
  console.log('7. Closing Viewer and navigating to DRHP tab in Transaction...');
  await page.click('.spatial-viewer-close');
  await page.waitForSelector('.spatial-viewer-modal', { state: 'detached' });

  // Navigate to Transaction overview
  const txId = 'bb5c6982-51ea-4f00-a6e0-f43aef6cdf09';
  await page.goto(`http://localhost:8080/transactions/${txId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.tab-strip', { timeout: 10000 });
  const drhpTab = page.locator('button.tab-button', { hasText: 'DRHP' }).first();
  await drhpTab.click();
  await page.waitForSelector('.drhp-panel', { timeout: 10000 });
  await page.waitForTimeout(1000);

  // Click the fact chip 82,45,00,000 in the compiled disclosure
  console.log('8. Clicking DRHP fact chip 82,45,00,000...');
  const factChip = page.locator('.fact-chip', { hasText: '82,45,00,000' }).first();
  await factChip.click();
  await page.waitForSelector('.drawer', { state: 'visible' });
  await page.waitForTimeout(500);

  const drawerScreenshot = path.join(OUT_DIR, 'spatial-evidence-drhp-fact-chip-drawer.png');
  await page.screenshot({ path: drawerScreenshot });
  console.log('✓ Captured:', drawerScreenshot);

  // Click "View Grounding in Source PDF" button in the evidence drawer
  console.log('9. Clicking "View Grounding in Source PDF"...');
  const groundingBtn = page.locator('button', { hasText: 'View Grounding in Source PDF' }).first();
  await groundingBtn.click();

  // Wait for viewer to open with pulsing target highlight
  await page.waitForSelector('.spatial-viewer-modal', { state: 'visible' });
  await page.waitForSelector('.spatial-bbox.targeted', { state: 'visible' });
  await page.waitForTimeout(1500);

  // Screenshot 4: DRHP Click-Through with glowing target bounding box on Page 3
  const clickthroughScreenshot = path.join(OUT_DIR, 'spatial-evidence-viewer-drhp-clickthrough.png');
  await page.screenshot({ path: clickthroughScreenshot });
  console.log('✓ Captured:', clickthroughScreenshot);

  await browser.close();
  console.log('=== ALL SCREENSHOTS CAPTURED SUCCESSFULLY! ===');
}

main().catch(err => {
  console.error('Screenshot capture failed:', err);
  process.exit(1);
});
