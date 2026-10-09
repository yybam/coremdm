'use strict';
// Sends an "update_available" FCM notification to every enrolled device.
// Run once after publishing a new release.
//
// Prerequisites:
//   npm install firebase-admin
//   Set GOOGLE_APPLICATION_CREDENTIALS to a service-account key JSON, OR run:
//     gcloud auth application-default login
//
// Usage:
//   node scripts/broadcast-update.js --version 0.3.0 --url https://coremdm.web.app/install

const admin = require('firebase-admin');

const args = process.argv.slice(2).reduce((acc, val, i, arr) => {
  if (val.startsWith('--')) acc[val.slice(2)] = arr[i + 1];
  return acc;
}, {});

const VERSION = args.version || '0.3.0';
const URL     = args.url     || 'https://coremdm.web.app/install';
const PROJECT = 'techeaz-core-mdm';

admin.initializeApp({ projectId: PROJECT });
const db = admin.firestore();
const messaging = admin.messaging();

async function main() {
  console.log(`Broadcasting update v${VERSION} to all devices...`);

  const snap = await db.collection('devices').get();
  const tokens = snap.docs
    .map(d => d.get('fcmToken'))
    .filter(Boolean);

  console.log(`Found ${tokens.length} device tokens`);
  if (tokens.length === 0) {
    console.log('No tokens found — nothing to send.');
    return;
  }

  // FCM allows max 500 tokens per multicast call
  const chunks = [];
  for (let i = 0; i < tokens.length; i += 500) chunks.push(tokens.slice(i, i + 500));

  let sent = 0, failed = 0;
  for (const chunk of chunks) {
    const response = await messaging.sendEachForMulticast({
      tokens: chunk,
      data: {
        type:    'update_available',
        version: VERSION,
        url:     URL,
      },
      notification: {
        title: 'CoreMDM Update Available',
        body:  `Version ${VERSION} is ready — tap to download.`,
      },
      android: {
        priority: 'high',
        notification: { channelId: 'mdm_update' },
      },
    });
    sent   += response.successCount;
    failed += response.failureCount;
    response.responses.forEach((r, i) => {
      if (!r.success) console.warn(`  Token ${i} failed: ${r.error?.message}`);
    });
  }

  console.log(`Done. Sent: ${sent}, Failed: ${failed}`);
}

main().catch(err => { console.error(err); process.exit(1); });
