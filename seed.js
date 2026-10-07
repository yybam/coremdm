process.env.FIRESTORE_EMULATOR_HOST = '';
const { initializeApp, cert, applicationDefault } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');

const projectId = process.env.FIREBASE_PROJECT_ID;
if (!projectId) throw new Error('Set FIREBASE_PROJECT_ID before running the seed script');
const app = initializeApp({ projectId });
const db = getFirestore(app);

// Simple known token easy to verify but complex enough to test the system
const TOKEN = 'TESTTOKEN-MDM-ENROLL-2024';
const IMEI  = '356938035643809';  // valid Luhn IMEI

const expires = new Date(Date.now() + 72 * 3600 * 1000);

async function seed() {
  const invRef = db.collection('device_inventory').doc(IMEI);
  const existing = await invRef.get();
  if (existing.exists) {
    await invRef.delete();
    console.log('Deleted existing record');
  }
  await invRef.set({
    imei: IMEI,
    tenantId: 'default',
    enrollmentStatus: 'PENDING',
    enrollmentToken: TOKEN,
    enrollmentTokenExpiresAt: Timestamp.fromDate(expires),
    enrolledAt: null,
    createdBy: 'yybambi23@gmail.com',
    createdAt: Timestamp.now()
  });
  const tokRef = db.collection('enrollment_tokens').doc(TOKEN);
  const tokExisting = await tokRef.get();
  if (tokExisting.exists) await tokRef.delete();
  await tokRef.set({
    imei: IMEI,
    expiresAt: Timestamp.fromDate(expires),
    createdAt: Timestamp.now()
  });
  console.log('OK');
  console.log('TOKEN: ' + TOKEN);
  console.log('IMEI:  ' + IMEI);
  process.exit(0);
}
seed().catch(e => { console.error(e.message); process.exit(1); });
