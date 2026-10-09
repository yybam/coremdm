'use strict';
// Usage:
//   1. Get a token:  firebase login:ci
//   2. Run:          $env:FIREBASE_TOKEN="ya29.xxx"; node scripts/broadcast-update.js 0.3.0

const https   = require('https');

const VERSION    = process.argv[2] || '0.3.0';
const DOWNLOAD   = process.argv[3] || 'https://coremdm.web.app/install';
const PROJECT_ID = 'techeaz-core-mdm';
const FS_BASE    = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const FCM_BASE   = `https://fcm.googleapis.com/v1/projects/${PROJECT_ID}/messages:send`;
const AUTH_TOKEN = process.env.FIREBASE_TOKEN;

if (!AUTH_TOKEN) {
  console.error('Set FIREBASE_TOKEN env var first:\n  1. Run:  firebase login:ci\n  2. Then: $env:FIREBASE_TOKEN="<token>"; node scripts/broadcast-update.js 0.3.0');
  process.exit(1);
}

function request(method, url, body) {
  return new Promise((resolve, reject) => {
    const u    = new URL(url);
    const data = body ? JSON.stringify(body) : null;
    const req  = https.request({
      hostname: u.hostname, path: u.pathname + u.search, method,
      headers: {
        Authorization:  `Bearer ${AUTH_TOKEN}`,
        'Content-Type': 'application/json',
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
      },
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(buf) }); }
        catch { resolve({ status: res.statusCode, body: buf }); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function getDeviceTokens() {
  const res = await request('GET', `${FS_BASE}/devices`);
  if (res.status !== 200) throw new Error(`Firestore ${res.status}: ${JSON.stringify(res.body)}`);
  return (res.body.documents || [])
    .map(d => d.fields?.fcmToken?.stringValue)
    .filter(Boolean);
}

async function sendFcm(fcmToken) {
  return request('POST', FCM_BASE, {
    message: {
      token: fcmToken,
      data:  { type: 'update_available', version: VERSION, url: DOWNLOAD },
      notification: {
        title: 'CoreMDM Update Available',
        body:  `Version ${VERSION} is ready — tap to download and install.`,
      },
      android: {
        priority: 'high',
        notification: { channel_id: 'mdm_update' },
      },
    },
  });
}

async function main() {
  console.log(`Broadcasting v${VERSION} to all enrolled devices...`);
  const tokens = await getDeviceTokens();
  console.log(`Found ${tokens.length} device(s)`);
  if (!tokens.length) { console.log('Nothing to send.'); return; }

  let sent = 0, failed = 0;
  for (const tok of tokens) {
    const res = await sendFcm(tok);
    if (res.status === 200) { sent++; process.stdout.write('.'); }
    else { failed++; console.warn(`\nFailed (${res.status}): ${JSON.stringify(res.body)}`); }
  }
  console.log(`\nDone. Sent: ${sent}  Failed: ${failed}`);
}

main().catch(e => { console.error(e.message); process.exit(1); });
