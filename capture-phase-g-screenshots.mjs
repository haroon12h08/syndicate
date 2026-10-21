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

  console.log('2. Logging in as Haroon...');
  await page.fill('input[type="email"]', 'haroon@trinitycapital.com');
  await page.fill('input[type="password"]', 'haroon@trinity');
  await page.click('button[type="submit"]');

  await page.waitForURL('**/home', { timeout: 10000 });
  await page.waitForSelector('.app-sidebar');

  // Go to Financial Due Diligence workstream
  const finWsId = '8939f57f-3cfe-4d9c-8aa0-4fe12682de2f';
  console.log('3. Navigating to Workstream Detail...');
  await page.goto(`http://localhost:8080/workstreams/${finWsId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.data-table');
  await page.waitForTimeout(1000);

  // Screenshot 1: Evidence table showing AI Document Classification suggestion
  console.log('4. Capturing Evidence table with AI Document Classification suggestion...');
  const screen1Brain = path.join(OUT_DIR, 'phase-g-ai-classification-suggestion.png');
  const screen1Pub = path.join(PUBLIC_DIR, 'phase-g-ai-classification-suggestion.png');
  await page.screenshot({ path: screen1Brain });
  fs.copyFileSync(screen1Brain, screen1Pub);
  console.log('✓ Captured:', screen1Pub);

  // Scroll down to Candidate Facts
  console.log('5. Scrolling to Candidate Facts with AI badges...');
  await page.evaluate(() => window.scrollTo(0, 500));
  await page.waitForTimeout(800);

  // Screenshot 2: Candidate Facts showing 🤖 AI Candidate badges
  const screen2Brain = path.join(OUT_DIR, 'phase-g-ai-candidates-list.png');
  const screen2Pub = path.join(PUBLIC_DIR, 'phase-g-ai-candidates-list.png');
  await page.screenshot({ path: screen2Brain });
  fs.copyFileSync(screen2Brain, screen2Pub);
  console.log('✓ Captured:', screen2Pub);

  // Click on the first AI Candidate badge to open the Provenance Manifest modal
  console.log('6. Opening AI Provenance Manifest modal...');
  const aiBadge = page.locator('button.badge-ai').first();
  await aiBadge.click();

  await page.waitForSelector('.ai-provenance-modal', { state: 'visible' });
  await page.waitForTimeout(600);

  // Screenshot 3: AI Provenance Manifest Modal open
  const screen3Brain = path.join(OUT_DIR, 'phase-g-ai-provenance-modal.png');
  const screen3Pub = path.join(PUBLIC_DIR, 'phase-g-ai-provenance-modal.png');
  await page.screenshot({ path: screen3Brain });
  fs.copyFileSync(screen3Brain, screen3Pub);
  console.log('✓ Captured:', screen3Pub);

  // Close the modal
  await page.click('.ai-provenance-modal .icon-button');
  await page.waitForSelector('.ai-provenance-modal', { state: 'detached' });
  await page.waitForTimeout(400);

  // Accept the first candidate
  console.log('7. Reviewing and accepting first AI Candidate Fact...');
  const firstCard = page.locator('.candidate-fact-card').first();
  const acceptBtn = firstCard.locator('button', { hasText: 'Accept' });
  await acceptBtn.click();
  await page.waitForTimeout(1000);

  // Screenshot 4: After human acceptance
  const screen4Brain = path.join(OUT_DIR, 'phase-g-candidate-review-accepted.png');
  const screen4Pub = path.join(PUBLIC_DIR, 'phase-g-candidate-review-accepted.png');
  await page.screenshot({ path: screen4Brain });
  fs.copyFileSync(screen4Brain, screen4Pub);
  console.log('✓ Captured:', screen4Pub);

  await browser.close();
  console.log('All Phase G verification screenshots captured successfully!');
}

main().catch(err => {
  console.error('Fatal error in screenshot capture:', err);
  process.exit(1);
});
