// Records the demo walkthrough (docs/demo/demo.mp4 and demo.gif) and the README screenshots against a running demo.
//
// Start a fresh demo first, so the shop has its seeded stock and no orders:
//   docker compose down -v && ./start-demo.sh --simulator
// then, with Playwright and ffmpeg installed:
//   NODE_PATH="$(npm root -g)" node docs/demo/record-demo.cjs
//
// It drives the UI like a person would and lays captions over the page that fade in and out at each step, saying
// what happens and why. Waits for the model and for the incident agent are recorded too and fast-forwarded
// afterwards, with a badge that says so. It uses the real model, so a run costs some model usage.
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const BASE_URL = process.env.DEMO_URL ?? 'http://localhost:8080';
const OUT_DIR = __dirname;
const WIDTH = 1440;
const HEIGHT = 900;
const FAST_FORWARD = 8;

const OVERLAY = String.raw`
(() => {
  const css = ${'`'}
    #demo-overlay-root { position: fixed; inset: 0; pointer-events: none; z-index: 2147483647;
      font-family: Inter, system-ui, sans-serif; }
    .demo-caption { position: absolute; bottom: 32px; width: 560px; padding: 20px 24px 22px;
      background: rgba(16, 24, 40, 0.92); color: #fff; border-radius: 16px;
      box-shadow: 0 24px 48px -12px rgba(16, 24, 40, 0.45);
      opacity: 0; transform: translateY(16px); transition: opacity 700ms ease, transform 700ms ease; }
    .demo-caption.left { left: 32px; } .demo-caption.right { right: 32px; }
    .demo-caption.center { left: 50%; margin-left: -280px; }
    .demo-caption.top { bottom: auto; top: 84px; }
    .demo-caption.shown { opacity: 1; transform: none; }
    .demo-caption .step { display: inline-block; font-size: 12px; font-weight: 700; letter-spacing: 0.08em;
      text-transform: uppercase; color: #9ee6cf; margin-bottom: 6px; }
    .demo-caption h3 { margin: 0 0 8px; font-size: 21px; line-height: 1.25; font-weight: 700; }
    .demo-caption p { margin: 0; font-size: 15.5px; line-height: 1.5; color: #d0d5dd; }
    .demo-caption p b { color: #fff; font-weight: 600; }
    .demo-caption code { font-family: ui-monospace, Menlo, monospace; font-size: 13.5px; color: #c4bcff; }
    .demo-card { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
      background: radial-gradient(1200px 700px at 30% 20%, #1f6f5c 0%, #102a26 55%, #0b1220 100%);
      color: #fff; opacity: 0; transition: opacity 900ms ease; }
    .demo-card.shown { opacity: 1; }
    .demo-card .inner { width: 900px; }
    .demo-card .kicker { font-size: 15px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase;
      color: #9ee6cf; }
    .demo-card h1 { font-size: 52px; line-height: 1.1; margin: 14px 0 18px; font-weight: 800; }
    .demo-card .sub { font-size: 21px; line-height: 1.5; color: #d0d5dd; margin: 0 0 34px; }
    .demo-card ol { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
    .demo-card li { display: flex; gap: 16px; align-items: baseline; font-size: 20px; line-height: 1.4;
      opacity: 0; transform: translateX(-12px); transition: opacity 600ms ease, transform 600ms ease; }
    .demo-card.shown li { opacity: 1; transform: none; }
    .demo-card li .n { flex: none; width: 34px; height: 34px; border-radius: 50%; display: inline-flex;
      align-items: center; justify-content: center; background: rgba(158, 230, 207, 0.16); color: #9ee6cf;
      font-weight: 700; font-size: 16px; }
    .demo-card .foot { margin-top: 38px; font-size: 15px; color: #98a2b3; }
    .demo-badge { position: absolute; top: 80px; right: 32px; padding: 8px 14px; border-radius: 999px;
      background: #5b4bd6; color: #fff; font-size: 14px; font-weight: 700; letter-spacing: 0.02em;
      box-shadow: 0 8px 20px -6px rgba(91, 75, 214, 0.6); opacity: 0; transition: opacity 400ms ease; }
    .demo-badge.shown { opacity: 1; }
    .demo-spot { position: absolute; border: 3px solid #5b4bd6; border-radius: 14px;
      box-shadow: 0 0 0 6px rgba(91, 75, 214, 0.18); opacity: 0; transition: opacity 500ms ease; }
    .demo-spot.shown { opacity: 1; }
  ${'`'};
  const ensureRoot = () => {
    let root = document.getElementById('demo-overlay-root');
    if (!root) {
      const style = document.createElement('style');
      style.textContent = css;
      document.head.appendChild(style);
      root = document.createElement('div');
      root.id = 'demo-overlay-root';
      document.body.appendChild(root);
    }
    return root;
  };
  const fadeOut = (element) => {
    if (!element) return;
    element.classList.remove('shown');
    setTimeout(() => element.remove(), 1000);
  };
  window.__demo = {
    caption(step, title, body, position) {
      const root = ensureRoot();
      fadeOut(root.querySelector('.demo-caption.live'));
      const caption = document.createElement('div');
      caption.className = 'demo-caption live ' + (position || 'left');
      caption.innerHTML = (step ? '<span class="step">' + step + '</span>' : '') + '<h3>' + title + '</h3>' +
        (body ? '<p>' + body + '</p>' : '');
      root.appendChild(caption);
      requestAnimationFrame(() => requestAnimationFrame(() => caption.classList.add('shown')));
    },
    hideCaption() {
      const caption = document.querySelector('#demo-overlay-root .demo-caption.live');
      if (caption) caption.classList.remove('live');
      fadeOut(caption);
    },
    card(kicker, title, sub, items, foot) {
      const root = ensureRoot();
      const card = document.createElement('div');
      card.className = 'demo-card live';
      card.innerHTML = '<div class="inner"><div class="kicker">' + kicker + '</div><h1>' + title + '</h1>' +
        '<p class="sub">' + sub + '</p><ol>' +
        items.map((item, i) => '<li style="transition-delay:' + (500 + i * 450) + 'ms"><span class="n">' +
          (i + 1) + '</span><span>' + item + '</span></li>').join('') +
        '</ol>' + (foot ? '<div class="foot">' + foot + '</div>' : '') + '</div>';
      root.appendChild(card);
      requestAnimationFrame(() => requestAnimationFrame(() => card.classList.add('shown')));
    },
    hideCard() {
      const card = document.querySelector('#demo-overlay-root .demo-card.live');
      if (card) card.classList.remove('live');
      fadeOut(card);
    },
    badge(text) {
      const root = ensureRoot();
      let badge = root.querySelector('.demo-badge');
      if (!text) { if (badge) badge.classList.remove('shown'); return; }
      if (!badge) { badge = document.createElement('div'); badge.className = 'demo-badge'; root.appendChild(badge); }
      badge.textContent = text;
      requestAnimationFrame(() => badge.classList.add('shown'));
    },
    spot(selector, index) {
      const root = ensureRoot();
      fadeOut(root.querySelector('.demo-spot'));
      const target = document.querySelectorAll(selector)[index || 0];
      if (!target) return;
      const box = target.getBoundingClientRect();
      const spot = document.createElement('div');
      spot.className = 'demo-spot';
      Object.assign(spot.style, { left: box.left - 8 + 'px', top: box.top - 8 + 'px',
        width: box.width + 16 + 'px', height: box.height + 16 + 'px' });
      root.appendChild(spot);
      requestAnimationFrame(() => requestAnimationFrame(() => spot.classList.add('shown')));
    },
    unspot() { fadeOut(document.querySelector('#demo-overlay-root .demo-spot')); },
    hideAll(hidden) { ensureRoot().style.visibility = hidden ? 'hidden' : 'visible'; },
  };
})();
`;

async function main() {
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'demo-recording-'));
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: WIDTH, height: HEIGHT },
    deviceScaleFactor: 1,
    recordVideo: { dir: workDir, size: { width: WIDTH, height: HEIGHT } },
  });
  await context.addInitScript(OVERLAY);
  const page = await context.newPage();
  const startedAt = Date.now();
  const elapsed = () => (Date.now() - startedAt) / 1000;
  const fastSegments = [];

  const pause = (seconds) => page.waitForTimeout(seconds * 1000);
  const demo = (method, ...args) =>
    page.evaluate(([m, a]) => window.__demo[m](...a), [method, args]);
  const caption = async (step, title, body, position, hold) => {
    await demo('caption', step, title, body, position);
    if (hold) await pause(hold);
  };
  // Waits for something slow (the model, the incident agent), recorded in full and fast-forwarded later.
  const fastForward = async (label, waitFor) => {
    await demo('badge', `⏩ ${label} · ${FAST_FORWARD}× speed`);
    await pause(0.4);
    const start = elapsed();
    await waitFor();
    fastSegments.push([start, elapsed()]);
    await demo('badge', null);
  };
  const screenshot = async (name) => {
    await demo('hideAll', true);
    await page.screenshot({ path: path.join(OUT_DIR, name) });
    await demo('hideAll', false);
  };
  const nav = (label) => page.locator('.app-nav a', { hasText: label }).first().click();
  const tab = (label) => page.locator('nav.tabs a', { hasText: label }).first().click();

  await page.goto(`${BASE_URL}/shop`);
  await page.evaluate(() => localStorage.setItem('trailhead.customer', 'ada@example.com'));
  await page.reload();
  await page.locator('app-product-card, .product').first().waitFor();

  // Intro
  await demo(
    'card',
    'Governed AI agents on Spring Boot microservices',
    'Trailhead, an online shop',
    'The normal path stays plain, fast code. Claude agents step in where judgment is needed, and act only ' +
      'through MCP tools that enforce the rules and audit every call.',
    [
      'A customer orders through the <b>shopping assistant</b>, and pays herself',
      'Operations writes off damaged stock: the paid order can no longer be shipped',
      'The <b>incident agent</b> works the stock-out as a ServiceNow incident, within its limits',
      'Every step lands on one <b>audit trail</b>, and the customer is told what happened',
    ],
    'Recorded live: the real Claude model, the ServiceNow simulator and simulated card payments.',
  );
  await pause(9);
  await demo('hideCard');
  await pause(1.2);

  // 1. The shop and the assistant
  await caption(
    'Step 1 · The shop',
    'Ada is signed in and wants a headlamp',
    'Products, stock and prices come from catalog-service. The <b>shopping assistant</b> on the right is Claude, ' +
      'run by Spring AI in agent-service.',
    'left',
    6,
  );
  await caption(
    'Step 1 · Order through chat',
    'She asks the assistant',
    'The assistant has no database access. It can only call the tools on its allowlist, on the commerce MCP ' +
      'server: <code>search_products</code>, <code>propose_order</code>, <code>get_order</code>…',
    'left',
  );
  const composer = page.getByLabel('Message to the assistant');
  await composer.click();
  await composer.pressSequentially('I need a headlamp for night hikes. Can you put one in an order for me?', {
    delay: 35,
  });
  await pause(0.8);
  await page.getByRole('button', { name: 'Send' }).click();
  await pause(1.5);
  await caption(
    'Step 1 · Every tool call is governed',
    'The MCP server checks each call first',
    'It checks the agent’s token, its tool allowlist and its kill switch. Ada’s email is filled in by code, ' +
      'not by the model, so the assistant can only see and act on her own orders. Each call is audited.',
    'left',
  );
  await fastForward('Waiting for Claude', () =>
    page.locator('app-proposal-card').first().waitFor({ timeout: 180_000 }),
  );
  await pause(1);

  // 2. The proposal
  await demo('spot', 'app-proposal-card');
  await caption(
    'Step 2 · The agent proposes, the customer decides',
    'A proposed order, not an order',
    '<code>propose_order</code> only prepares the basket. <b>No agent sits on the checkout path</b>: only Ada’s own ' +
      'click on <b>Confirm and pay</b> places the order and charges her card.',
    'left',
    9,
  );
  await screenshot('1-chat-proposal.png');
  await demo('unspot');
  await caption(
    'Step 3 · Checkout is plain code',
    'Ada confirms and pays',
    'order-service places the order and reserves the stock, payment-service charges the test card. ' +
      'Deterministic and fast, with no model involved.',
    'left',
  );
  await pause(2);
  await page.getByRole('button', { name: 'Confirm and pay' }).click();
  await page.locator('app-proposal-card .callout[data-tone="success"]').waitFor({ timeout: 60_000 });
  await pause(1);
  await demo('spot', 'app-proposal-card .callout[data-tone="success"]');
  await pause(4);
  await demo('unspot');

  // My orders
  await nav('My orders');
  await page.locator('.order-row').first().waitFor();
  await caption(
    'Step 3 · Paid',
    'Ada sees her order, as a customer',
    'Paid orders wait here to be shipped. So far no agent was needed after the proposal: the happy path ' +
      'never calls a model.',
    'left',
    7,
  );

  // 4. The agents and their rules
  await nav('Operations');
  await page.locator('nav.tabs').waitFor();
  await tab('Agents');
  await page.locator('app-agent-controls .tools').first().waitFor();
  await caption(
    'Step 4 · The back office',
    'Each agent is defined in one file, and governed',
    'Its model, effort, tool-call budget and <b>allowed tools</b> per MCP server. The MCP servers refuse any ' +
      'other tool, and each agent has a <b>kill switch</b> that stops its tool calls at once.',
    'right',
    6,
  );
  await page.mouse.wheel(0, 420);
  await pause(4);
  await caption(
    'Step 4 · The incident agent',
    'It may cancel and refund, but only so far',
    'A refund above <b>€100</b> waits for a person to approve it. It may only act on the order linked to the ' +
      'incident it is working, and it can always hand the incident to a team.',
    'right',
    6,
  );
  await page.mouse.wheel(0, -2000);

  // 5. The write-off
  await tab('Demo controls');
  const productSelect = page.locator('app-demo-controls select').first();
  await productSelect.waitFor();
  const headlampId = await productSelect.locator('option', { hasText: 'Headlamp' }).first().getAttribute('value');
  await productSelect.selectOption(headlampId);
  const stockText = await page.locator('app-demo-controls small.muted').first().textContent();
  const onHand = Number(/(\d+) on hand/.exec(stockText)[1]);
  await caption(
    'Step 5 · Something goes wrong after payment',
    'Operations writes off damaged headlamps',
    `${stockText.trim()}. Writing off all ${onHand} leaves fewer units than are reserved, so Ada’s paid order ` +
      'can no longer be fulfilled.',
    'right',
    5,
  );
  const units = page.locator('app-demo-controls input[type="number"]').first();
  await units.fill(String(onHand));
  await units.dispatchEvent('change');
  const reason = page.locator('app-demo-controls .write-off-row input:not([type="number"])').first();
  await reason.fill('');
  await reason.pressSequentially('Water damage in the warehouse', { delay: 35 });
  await pause(1);
  await page.getByRole('button', { name: 'Write off' }).click();
  await page.locator('app-demo-controls .callout').first().waitFor();
  await demo('spot', 'app-demo-controls .callout');
  await caption(
    'Step 5 · A stock-out event',
    'The catalog publishes a stock-out on Kafka',
    'Through a transactional outbox, so the event is never lost and never sent for a change that did not happen. ' +
      'order-service hears it and opens a <b>STOCK_OUT case</b> for each order it can no longer fulfil.',
    'right',
    8,
  );
  await demo('unspot');

  // 6 and 7. The case and the incident agent
  await tab('Cases');
  await page.locator('app-case-card').first().waitFor({ timeout: 60_000 });
  await caption(
    'Step 6 · One case system',
    'The case becomes a ServiceNow incident',
    'It is created as an incident and assigned to the incident agent’s group. People see and change it in ' +
      'ServiceNow, and the shop mirrors its state here.',
    'right',
    6,
  );
  await caption(
    'Step 7 · The incident agent works it first',
    'Reads, checks, acts, documents',
    '<code>get_incident</code> and <code>get_order</code> for the facts, then <code>cancel_order</code>, ' +
      '<code>issue_refund</code> (€39.50, under its limit), <code>notify_customer</code>, ' +
      '<code>add_work_note</code> and <code>resolve_incident</code>. Each call is checked and audited.',
    'right',
  );
  await fastForward('Incident agent at work', () =>
    page.waitForFunction(
      () => [...document.querySelectorAll('app-case-card')].some((card) => /Resolved/.test(card.textContent)),
      null,
      { timeout: 300_000, polling: 1000 },
    ),
  );
  await pause(1);
  await demo('spot', 'app-case-card');
  await caption(
    'Step 7 · Resolved, with no person needed',
    'The agent stayed inside its policy',
    'Had the refund been above €100, or the payment service down, it would have handed the incident to the ' +
      'Payments team instead, with a note saying exactly what they need to do.',
    'right',
    7,
  );
  await screenshot('2-case-resolved.png');
  await demo('unspot');

  // 8. The audit trail
  await nav('All orders');
  await page.locator('a[href*="/orders/"]').first().waitFor();
  await page.locator('a[href*="/orders/"]').first().click();
  await page.locator('ol.audit-timeline li').first().waitFor({ timeout: 60_000 });
  await caption(
    'Step 8 · One audit trail',
    'The back office sees who did what',
    'The order is cancelled and refunded. Its timeline shows every step: by the shop itself, by people, ' +
      'and by each agent, with the tool it used.',
    'left',
    6,
  );
  await page.locator('ol.audit-timeline').scrollIntoViewIfNeeded();
  await page.mouse.wheel(0, 200);
  await pause(1.5);
  await caption(
    'Step 8 · Linked to traces',
    'From the purchase to the refund',
    'Agent steps in purple, people in orange, the system in grey. Every entry links to its trace in Jaeger, ' +
      'so you can follow one step across all the services it touched.',
    'left',
    9,
  );
  await screenshot('3-order-timeline.png');

  // 9. The customer
  await page.mouse.wheel(0, -3000);
  await nav('My orders');
  await page.locator('.inbox li').first().waitFor({ timeout: 60_000 });
  await demo('spot', '.inbox');
  await caption(
    'Step 9 · The customer is told the truth',
    'Ada gets the agent’s message',
    'She sees the outcome in plain words: the order is cancelled and €39.50 is back on her card. ' +
      'Who did it internally stays in the back office.',
    'left',
    9,
  );
  await screenshot('4-customer-inbox.png');
  await demo('unspot');
  await page.locator('.order-row').first().click();
  await page.locator('app-order-progress').first().waitFor();
  await caption(
    'Step 9 · Her order page',
    'Cancelled and refunded',
    'The refund went through payment-service with an idempotency key, so a retry can never pay her twice.',
    'left',
    7,
  );
  await demo('hideCaption');
  await pause(1);

  // Outro
  await demo(
    'card',
    'What you just saw',
    'Agents where judgment is needed, rules where they are not',
    'The models decide what to do. The MCP servers decide what they are allowed to do.',
    [
      '<b>No AI on the checkout path</b>: agents propose, customers confirm and pay',
      'Agents act only through MCP tools: <b>allowlist, customer scoping, refund limit, kill switch</b>',
      'Every problem is a <b>ServiceNow incident</b>, worked by an agent first and handed to a team when needed',
      'Every action, by an agent, a person or the system, is <b>audited and traced</b>',
    ],
    'github.com/exepex/spring-ai-agentic-commerce',
  );
  await pause(10);

  const video = page.video();
  await context.close();
  await browser.close();
  const raw = await video.path();
  console.log('Fast-forwarded segments (s):', JSON.stringify(fastSegments));
  render(raw, fastSegments, workDir);
}

/** Cuts the raw recording into normal and fast-forwarded parts, then writes the MP4 and the GIF. */
function render(raw, fastSegments, workDir) {
  const parts = [];
  let cursor = 0;
  for (const [start, end] of fastSegments) {
    parts.push({ start: cursor, end: start, speed: 1 });
    parts.push({ start, end, speed: FAST_FORWARD });
    cursor = end;
  }
  parts.push({ start: cursor, end: null, speed: 1 });

  const filters = parts.map((part, i) => {
    const trim = part.end === null ? `trim=start=${part.start}` : `trim=start=${part.start}:end=${part.end}`;
    return `[0:v]${trim},setpts=(PTS-STARTPTS)/${part.speed}[p${i}]`;
  });
  const graph = `${filters.join(';')};${parts.map((_, i) => `[p${i}]`).join('')}concat=n=${parts.length}:v=1:a=0,fps=25[v]`;
  const mp4 = path.join(OUT_DIR, 'demo.mp4');
  execFileSync('ffmpeg', ['-y', '-v', 'error', '-i', raw, '-filter_complex', graph, '-map', '[v]', '-c:v', 'libx264',
    '-preset', 'slow', '-crf', '26', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', mp4], { stdio: 'inherit' });

  const palette = path.join(workDir, 'palette.png');
  const gifScale = 'fps=8,scale=960:-1:flags=lanczos';
  execFileSync('ffmpeg', ['-y', '-v', 'error', '-i', mp4, '-vf', `${gifScale},palettegen=max_colors=128:stats_mode=diff`,
    palette], { stdio: 'inherit' });
  execFileSync('ffmpeg', ['-y', '-v', 'error', '-i', mp4, '-i', palette, '-lavfi',
    `${gifScale}[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle`,
    path.join(OUT_DIR, 'demo.gif')], { stdio: 'inherit' });
  console.log('Wrote demo.mp4, demo.gif and the screenshots to', OUT_DIR);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
