'use strict';
require('dotenv').config();

const express  = require('express');
const http     = require('http');
const https    = require('https');
const { WebSocketServer, WebSocket } = require('ws');
const admin    = require('firebase-admin');
const path     = require('path');
const crypto   = require('crypto');

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'techeaz-core-mdm';
const FS_BASE    = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Firebase Admin — only used for verifyIdToken (uses Google public keys, no credentials needed)
admin.initializeApp({ projectId: PROJECT_ID });

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocketServer({ server });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ── Connection maps ──────────────────────────────────────────────────────────
const deviceConnections = new Map(); // deviceId → { ws, uid, sessionId }
const adminConnections  = new Map(); // sessionId → { ws, uid, deviceId|null }

// ── Helpers ──────────────────────────────────────────────────────────────────
function send(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

async function verifyToken(idToken) {
  return admin.auth().verifyIdToken(idToken);
}

// Firestore REST helpers — use the caller's own idToken so security rules apply
function fsGet(path, idToken) {
  return new Promise((resolve, reject) => {
    const url = new URL(`${FS_BASE}/${path}`);
    const req = https.get(url, { headers: { Authorization: `Bearer ${idToken}` } }, res => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => {
        try { resolve(JSON.parse(body)); }
        catch { reject(new Error('bad json: ' + body)); }
      });
    });
    req.on('error', reject);
  });
}

function fsPatch(path, fields, idToken) {
  return new Promise((resolve, reject) => {
    const fieldPaths = Object.keys(fields).join(',');
    const url = `${FS_BASE}/${path}?updateMask.fieldPaths=${fieldPaths}`;
    const body = JSON.stringify({
      fields: Object.fromEntries(
        Object.entries(fields).map(([k, v]) => [k, fsValue(v)])
      )
    });
    const opts = {
      method : 'PATCH',
      headers: {
        Authorization : `Bearer ${idToken}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      }
    };
    const req = https.request(url, opts, res => {
      let b = '';
      res.on('data', d => b += d);
      res.on('end', () => resolve(b));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function fsQuery(collection, field, value, idToken) {
  return new Promise((resolve, reject) => {
    const url = `${FS_BASE}:runQuery`;
    const body = JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: collection }],
        where: { fieldFilter: { field: { fieldPath: field }, op: 'EQUAL', value: fsValue(value) } }
      }
    });
    const opts = {
      method : 'POST',
      headers: {
        Authorization : `Bearer ${idToken}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      }
    };
    const req = https.request(url, opts, res => {
      let b = '';
      res.on('data', d => b += d);
      res.on('end', () => {
        try { resolve(JSON.parse(b)); }
        catch { reject(new Error('bad json: ' + b)); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function fsValue(v) {
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number')  return { integerValue: String(v) };
  if (v === null)             return { nullValue: null };
  return { stringValue: String(v) };
}

function parseDoc(doc) {
  if (!doc || !doc.fields) return null;
  const f = doc.fields;
  const id = (doc.name || '').split('/').pop();
  return {
    id,
    model      : f.model?.stringValue       || 'Unknown Device',
    osVersion  : f.osVersion?.stringValue   || '',
    imei       : f.imei?.stringValue        || null,
    serial     : f.serial?.stringValue      || null,
    androidId  : f.androidId?.stringValue   || null,
    status     : f.status?.stringValue      || 'offline',
    alarmActive: f.alarmActive?.booleanValue || false,
    ownerId    : f.ownerId?.stringValue      || null,
    lastSeen   : null,
  };
}

async function deviceBelongsToUser(deviceId, uid, idToken) {
  try {
    const doc = await fsGet(`devices/${deviceId}`, idToken);
    return doc.fields?.ownerId?.stringValue === uid;
  } catch { return false; }
}

// ── REST — health ────────────────────────────────────────────────────────────
app.get('/health', (_req, res) => res.json({ ok: true, project: PROJECT_ID }));

// ── REST — list devices ──────────────────────────────────────────────────────
app.get('/api/devices', async (req, res) => {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ')) return res.status(401).json({ error: 'Missing Bearer token' });
  const idToken = auth.slice(7);
  try {
    const { uid } = await verifyToken(idToken);
    const results = await fsQuery('devices', 'ownerId', uid, idToken);
    const devices = (Array.isArray(results) ? results : [])
      .map(r => parseDoc(r.document))
      .filter(Boolean);
    res.json(devices);
  } catch (e) {
    console.error('/api/devices error:', e.message);
    res.status(401).json({ error: e.message });
  }
});

// ── REST — alarm ─────────────────────────────────────────────────────────────
app.post('/api/devices/:deviceId/alarm', async (req, res) => {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ')) return res.status(401).json({ error: 'Missing Bearer token' });
  const idToken = auth.slice(7);
  try {
    const { uid }      = await verifyToken(idToken);
    const { deviceId } = req.params;
    if (!(await deviceBelongsToUser(deviceId, uid, idToken)))
      return res.status(403).json({ error: 'Not your device' });

    const active = req.body.active !== false;
    await fsPatch(`devices/${deviceId}`, { alarmActive: active }, idToken);

    // Also push over WS if device is live
    const conn = deviceConnections.get(deviceId);
    if (conn) send(conn.ws, { type: 'alarm', active });

    res.json({ ok: true, active });
  } catch (e) {
    console.error('/api/devices/alarm error:', e.message);
    res.status(500).json({ error: e.message });
  }
});

// ── REST — send policy command to device ─────────────────────────────────────
app.post('/api/devices/:deviceId/command', async (req, res) => {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ')) return res.status(401).json({ error: 'Missing Bearer token' });
  const idToken = auth.slice(7);
  try {
    const { uid }      = await verifyToken(idToken);
    const { deviceId } = req.params;
    if (!(await deviceBelongsToUser(deviceId, uid, idToken)))
      return res.status(403).json({ error: 'Not your device' });

    const { command, payload } = req.body;
    if (!command) return res.status(400).json({ error: 'command required' });

    // Push command over WS if device is connected
    const conn = deviceConnections.get(deviceId);
    if (conn) {
      send(conn.ws, { type: 'command', command, payload: payload || {} });
      res.json({ ok: true, delivered: 'ws' });
    } else {
      // Store as pending command in Firestore for next time device connects
      await fsPatch(`devices/${deviceId}`, { pendingCommand: command }, idToken);
      res.json({ ok: true, delivered: 'pending' });
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── WebSocket handler ─────────────────────────────────────────────────────────
wss.on('connection', ws => {
  let role      = null;
  let uid       = null;
  let idToken   = null;
  let deviceId  = null;
  let sessionId = null;

  const authTimeout = setTimeout(() => {
    if (!uid) ws.close(4001, 'Auth timeout');
  }, 15_000);

  ws.on('message', async raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    // ── AUTH ─────────────────────────────────────────────────────────────────
    if (msg.type === 'auth') {
      clearTimeout(authTimeout);
      try {
        const decoded = await verifyToken(msg.idToken);
        uid       = decoded.uid;
        idToken   = msg.idToken;
        role      = msg.role || 'admin';
        sessionId = crypto.randomUUID();

        if (role === 'device') {
          deviceId = msg.deviceId;
          if (!(await deviceBelongsToUser(deviceId, uid, idToken))) {
            send(ws, { type: 'error', message: 'Not your device' });
            return ws.close(4003, 'Not your device');
          }
          const old = deviceConnections.get(deviceId);
          if (old) old.ws.close();

          deviceConnections.set(deviceId, { ws, uid, sessionId });
          await fsPatch(`devices/${deviceId}`, { status: 'online' }, idToken).catch(() => {});
          send(ws, { type: 'auth_ok', sessionId });
          console.log(`[WS] Device online: ${deviceId}`);

        } else {
          adminConnections.set(sessionId, { ws, uid, idToken, deviceId: null });
          send(ws, { type: 'auth_ok', sessionId });
          console.log(`[WS] Admin online: ${uid} sid=${sessionId}`);
        }
      } catch (e) {
        send(ws, { type: 'error', message: 'Auth failed: ' + e.message });
        ws.close(4001, 'Auth failed');
      }
      return;
    }

    if (!uid) return;

    // ── Token refresh from client ─────────────────────────────────────────────
    if (msg.type === 'token_refresh') {
      idToken = msg.idToken;
      if (role === 'admin' && sessionId) {
        const info = adminConnections.get(sessionId);
        if (info) info.idToken = idToken;
      }
      return;
    }

    // ── Admin → Device: request control ──────────────────────────────────────
    if (msg.type === 'control_request' && role === 'admin') {
      const target    = msg.deviceId;
      const adminInfo = adminConnections.get(sessionId);
      if (!(await deviceBelongsToUser(target, uid, adminInfo?.idToken || idToken)))
        return send(ws, { type: 'error', message: 'Not your device' });

      const dc = deviceConnections.get(target);
      if (!dc) return send(ws, { type: 'error', message: 'Device not connected' });

      adminConnections.get(sessionId).deviceId = target;
      send(dc.ws, { type: 'control_request', adminSessionId: sessionId });
      send(ws,   { type: 'control_request_sent', deviceId: target });
      return;
    }

    // ── Device → Admin: WebRTC offer ──────────────────────────────────────────
    if (msg.type === 'offer' && role === 'device') {
      const ac = adminConnections.get(msg.adminSessionId);
      if (ac) send(ac.ws, { type: 'offer', sdp: msg.sdp, deviceId });
      return;
    }

    // ── Admin → Device: WebRTC answer ─────────────────────────────────────────
    if (msg.type === 'answer' && role === 'admin') {
      const info = adminConnections.get(sessionId);
      const dc   = deviceConnections.get(info?.deviceId || msg.deviceId);
      if (dc) send(dc.ws, { type: 'answer', sdp: msg.sdp, adminSessionId: sessionId });
      return;
    }

    // ── ICE candidate relay ────────────────────────────────────────────────────
    if (msg.type === 'ice') {
      if (role === 'admin') {
        const info = adminConnections.get(sessionId);
        const dc   = deviceConnections.get(info?.deviceId || msg.deviceId);
        if (dc) send(dc.ws, { type: 'ice', candidate: msg.candidate, adminSessionId: sessionId });
      } else if (role === 'device') {
        const ac = adminConnections.get(msg.adminSessionId);
        if (ac) send(ac.ws, { type: 'ice', candidate: msg.candidate, deviceId });
      }
      return;
    }

    // ── Alarm via WS ──────────────────────────────────────────────────────────
    if (msg.type === 'alarm' && role === 'admin') {
      const target    = msg.deviceId;
      const adminInfo = adminConnections.get(sessionId);
      if (!(await deviceBelongsToUser(target, uid, adminInfo?.idToken || idToken))) return;
      const active = msg.active !== false;
      const dc     = deviceConnections.get(target);
      if (dc) send(dc.ws, { type: 'alarm', active });
      fsPatch(`devices/${target}`, { alarmActive: active }, adminInfo?.idToken || idToken).catch(() => {});
      return;
    }

    // ── Disconnect / end session ──────────────────────────────────────────────
    if (msg.type === 'disconnect') {
      if (role === 'admin') {
        const info = adminConnections.get(sessionId);
        const dc   = info?.deviceId && deviceConnections.get(info.deviceId);
        if (dc) send(dc.ws, { type: 'admin_disconnected', adminSessionId: sessionId });
      } else if (role === 'device') {
        for (const [, info] of adminConnections) {
          if (info.deviceId === deviceId)
            send(info.ws, { type: 'device_disconnected', deviceId });
        }
      }
    }
  });

  ws.on('close', () => {
    clearTimeout(authTimeout);
    if (role === 'device' && deviceId) {
      deviceConnections.delete(deviceId);
      if (idToken) {
        fsPatch(`devices/${deviceId}`, { status: 'offline' }, idToken).catch(() => {});
      }
      for (const [, info] of adminConnections) {
        if (info.deviceId === deviceId) {
          send(info.ws, { type: 'device_disconnected', deviceId });
          info.deviceId = null;
        }
      }
      console.log(`[WS] Device offline: ${deviceId}`);
    } else if (role === 'admin' && sessionId) {
      adminConnections.delete(sessionId);
      console.log(`[WS] Admin offline: ${uid} sid=${sessionId}`);
    }
  });

  ws.on('error', err => console.error('[WS] error:', err.message));
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`[MDM] Server listening on http://localhost:${PORT}`));
