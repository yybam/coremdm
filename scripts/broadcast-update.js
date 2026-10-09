'use strict';
// Push an "update available" notification to every enrolled device over FCM.
//
// Usage (no token needed — reads credentials from your firebase login session):
//   node scripts/broadcast-update.js 0.4.8
//
// You can still override with an explicit token if you prefer:
//   $env:FIREBASE_TOKEN="ya29.xxx"; node scripts/broadcast-update.js 0.4.8

const https   = require('https');
const os      = require('os');
const path    = require('path');
const fs      = require('fs');

const VERSION    = process.argv[2] || 'latest';
const DOWNLOAD   = process.argv[3] || 'https://coremdm.web.app/install';
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'techeaz-core-mdm';
const FS_BASE    = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const FCM_BASE   = `https://fcm.googleapis.com/v1/projects/${PROJECT_ID}/messages:send`;

// ── Token resolution ──────────────────────────────────────────────────────────
// Order: 1) explicit env var  2) firebase CLI stored session  3) error

async function resolveToken() {
  if (process.env.FIREBASE_TOKEN) return process.env.FIREBASE_TOKEN;

  const configPath = path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json');
  if (!fs.existsSync(configPath)) {
    throw new Error(
      'Not logged in to Firebase. Run:\n  firebase login\nthen retry.'
    );
  }

  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  const tokens = config.tokens;
  if (!tokens?.refresh_token) {
    throw new Error('No refresh token found. Run: firebase login');
  }

  // Use the access_token directly if it hasn't expired yet.
  const expiresAt = tokens.expires_at || 0;
  if (Date.now() < expiresAt - 60_000 && tokens.access_token) {
    return tokens.access_token;
  }

  // Refresh using the Firebase CLI's well-known OAuth client.
  return refreshAccessToken(
    tokens.refresh_token,
    configPath,
    config
  );
}

function getFirebaseOAuthCredentials() {
  // Read client_id / client_secret from the locally-installed firebase-tools
  // package so no secrets are hardcoded in this script.
  try {
    const npmGlobal = require('child_process')
      .execSync('npm root -g', { encoding: 'utf8' }).trim();
    const apiPath = path.join(npmGlobal, 'firebase-tools', 'lib', 'api.js');
    if (!fs.existsSync(apiPath)) throw new Error('api.js not found');
    const src = fs.readFileSync(apiPath, 'utf8');
    const idMatch     = src.match(/clientId\s*=\s*.*?"([^"]+)"/);
    const secretMatch = src.match(/clientSecret\s*=\s*.*?"([^"]+)"/);
    if (!idMatch || !secretMatch) throw new Error('credentials not found in api.js');
    return { client_id: idMatch[1], client_secret: secretMatch[1] };
  } catch (e) {
    throw new Error(`Could not read Firebase CLI credentials: ${e.message}\nRun: npm install -g firebase-tools`);
  }
}

function refreshAccessToken(refreshToken, configPath, config) {
  const { client_id, client_secret } = getFirebaseOAuthCredentials();
  return new Promise((resolve, reject) => {
    const body = new URLSearchParams({
      client_id,
      client_secret,
      grant_type:    'refresh_token',
      refresh_token: refreshToken,
    }).toString();

    const req = https.request({
      hostname: 'oauth2.googleapis.com',
      path:     '/token',
      method:   'POST',
      headers: {
        'Content-Type':   'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(body),
      },
    }, res => {
      let buf = '';
      res.on('data', d => buf += d);
      res.on('end', () => {
        try {
          const data = JSON.parse(buf);
          if (!data.access_token) return reject(new Error(`Token refresh failed: ${buf}`));

          // Persist the new access token back to the config so the next run is faster.
          try {
            config.tokens = {
              ...config.tokens,
              access_token: data.access_token,
              expires_in:   data.expires_in,
              expires_at:   Date.now() + (data.expires_in * 1000),
            };
            fs.writeFileSync(configPath, JSON.stringify(config, null, 2));
          } catch { /* non-fatal */ }

          resolve(data.access_token);
        } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

// ── HTTP helper ───────────────────────────────────────────────────────────────

function request(method, url, body, token) {
  return new Promise((resolve, reject) => {
    const u    = new URL(url);
    const data = body ? JSON.stringify(body) : null;
    const req  = https.request({
      hostname: u.hostname, path: u.pathname + u.search, method,
      headers: {
        Authorization:  `Bearer ${token}`,
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

// ── Firestore + FCM ───────────────────────────────────────────────────────────

async function getDeviceTokens(token) {
  const res = await request('GET', `${FS_BASE}/devices`, null, token);
  if (res.status !== 200) throw new Error(`Firestore ${res.status}: ${JSON.stringify(res.body)}`);
  return (res.body.documents || [])
    .map(d => d.fields?.fcmToken?.stringValue)
    .filter(Boolean);
}

async function sendFcm(fcmToken, authToken) {
  return request('POST', FCM_BASE, {
    message: {
      token: fcmToken,
      data:  { type: 'update_available', version: VERSION, url: DOWNLOAD },
      notification: {
        title: 'CORE MDM Update Available',
        body:  `Version ${VERSION} is ready — tap to download and install.`,
      },
      android: {
        priority: 'high',
        notification: { channel_id: 'mdm_update' },
      },
    },
  }, authToken);
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`Resolving credentials...`);
  const authToken = await resolveToken();
  console.log(`Broadcasting v${VERSION} update to all enrolled devices...`);

  const tokens = await getDeviceTokens(authToken);
  console.log(`Found ${tokens.length} enrolled device(s)`);
  if (!tokens.length) { console.log('No devices to notify.'); return; }

  let sent = 0, failed = 0;
  for (const tok of tokens) {
    const res = await sendFcm(tok, authToken);
    if (res.status === 200) { sent++; process.stdout.write('.'); }
    else { failed++; console.warn(`\nFailed (${res.status}): ${JSON.stringify(res.body)}`); }
  }
  console.log(`\nDone. Sent: ${sent}  Failed: ${failed}`);
}

main().catch(e => { console.error('\nError:', e.message); process.exit(1); });
