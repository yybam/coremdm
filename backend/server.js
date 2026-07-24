'use strict';
require('dotenv').config();

const express = require('express');
const http    = require('http');
const { WebSocketServer } = require('ws');
const admin   = require('firebase-admin');

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'techeaz-core-mdm';

// ---------------------------------------------------------------------------
// Firebase Admin — initialised with just projectId.
// verifyIdToken() fetches Google's public keys from a public endpoint and
// only needs the projectId to validate the `aud` claim; no service account
// is required for that operation.
// ---------------------------------------------------------------------------
admin.initializeApp({ projectId: PROJECT_ID });

// ---------------------------------------------------------------------------
// Device ownership check via Firestore REST API.
// We forward the user's own ID token so Firestore security rules enforce
// ownership — no Admin credentials needed.
// ---------------------------------------------------------------------------
async function deviceBelongsToUser(uid, deviceId, idToken) {
  const url = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/users/${uid}/devices/${deviceId}`;
  const res  = await fetch(url, { headers: { Authorization: `Bearer ${idToken}` } });
  return res.ok; // 200 → exists & owned; 403/404 → not owned or not found
}

// ---------------------------------------------------------------------------
// Express app
// ---------------------------------------------------------------------------
const app = express();
app.use(express.json());

// Health check
app.get('/health', (_req, res) => res.json({ ok: true, project: PROJECT_ID }));

// POST /auth/verify
// Body: { idToken: string }
// Returns: { uid, email } or 401
app.post('/auth/verify', async (req, res) => {
  const { idToken } = req.body;
  if (!idToken) return res.status(400).json({ error: 'idToken required' });
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    res.json({ uid: decoded.uid, email: decoded.email ?? null });
  } catch (err) {
    res.status(401).json({ error: 'Invalid or expired token', detail: err.message });
  }
});

// POST /devices/:deviceId/alarm
// Body: { idToken: string, active: boolean }
// Writes alarmActive via Firestore REST API (user's own token, no admin needed)
app.post('/devices/:deviceId/alarm', async (req, res) => {
  const { idToken, active } = req.body;
  const { deviceId }        = req.params;
  if (!idToken)               return res.status(400).json({ error: 'idToken required' });
  if (typeof active !== 'boolean') return res.status(400).json({ error: 'active (boolean) required' });

  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    const owned   = await deviceBelongsToUser(decoded.uid, deviceId, idToken);
    if (!owned) return res.status(403).json({ error: 'Device not found or not owned by this user' });

    // Write via Firestore REST API with user token — rules already enforce ownership
    const url  = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/users/${decoded.uid}/devices/${deviceId}?updateMask.fieldPaths=alarmActive`;
    const body = { fields: { alarmActive: { booleanValue: active } } };
    const patch = await fetch(url, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${idToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!patch.ok) {
      const err = await patch.text();
      return res.status(patch.status).json({ error: err });
    }
    res.json({ ok: true, deviceId, active });
  } catch (err) {
    const status = err.code?.startsWith('auth/') ? 401 : 500;
    res.status(status).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// WebSocket — token + ownership check on first message
// Client sends: { type:"auth", idToken:"<Firebase ID token>", deviceId:"<id>" }
// Server replies: { type:"auth_ok" } or closes with error code
// ---------------------------------------------------------------------------
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  let authed = false;

  const timeout = setTimeout(() => {
    if (!authed) ws.close(4001, 'Authentication timeout');
  }, 10_000);

  ws.once('message', async (raw) => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { ws.close(4000, 'Invalid JSON'); return; }

    if (msg.type !== 'auth' || !msg.idToken || !msg.deviceId) {
      ws.close(4000, 'First message must be { type:"auth", idToken, deviceId }');
      return;
    }

    try {
      const decoded = await admin.auth().verifyIdToken(msg.idToken);
      const owned   = await deviceBelongsToUser(decoded.uid, msg.deviceId, msg.idToken);
      if (!owned) { ws.close(4003, 'Device not owned by this user'); return; }

      authed = true;
      clearTimeout(timeout);
      ws.send(JSON.stringify({ type: 'auth_ok', uid: decoded.uid, deviceId: msg.deviceId }));

      ws.on('message', (data) => {
        console.log(`[ws] uid=${decoded.uid} device=${msg.deviceId}:`, data.toString().slice(0, 120));
      });
      ws.on('close', () => console.log(`[ws] disconnected uid=${decoded.uid} device=${msg.deviceId}`));
    } catch (err) {
      ws.close(4001, `Auth failed: ${err.message}`);
    }
  });
});

// ---------------------------------------------------------------------------
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`Core MDM backend listening on http://localhost:${PORT}`));
