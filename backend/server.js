/**
 * IFIX VPN REST API – Real License System
 * - Only pre-registered keys can be activated
 * - Device binding (maxDevices)
 * - Expiry check
 * - Admin endpoint to create new licenses
 */

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

// ── In-memory store (replace with PostgreSQL in production) ──────────────────
const database = {
  licenses: {
    'IFIX-VIP-PRO-2026': {
      licenseKey: 'IFIX-VIP-PRO-2026',
      status: 'ACTIVE',
      planType: 'VIP 1-Year Commercial Pass',
      expiryTimestamp: Date.now() + 365 * 24 * 3600 * 1000,
      maxDevices: 5,
      activeDevicesCount: 0,
      devices: [] // [{ deviceId, deviceName, activatedAt }]
    },
    'IFIX-PREMIUM-9999': {
      licenseKey: 'IFIX-PREMIUM-9999',
      status: 'ACTIVE',
      planType: 'Ultra High-Speed Pass',
      expiryTimestamp: Date.now() + 180 * 24 * 3600 * 1000,
      maxDevices: 5,
      activeDevicesCount: 0,
      devices: []
    },
    'IFIX-DEMO-TEST': {
      licenseKey: 'IFIX-DEMO-TEST',
      status: 'ACTIVE',
      planType: 'Tester Trial License (30 Days)',
      expiryTimestamp: Date.now() + 30 * 24 * 3600 * 1000,
      maxDevices: 2,
      activeDevicesCount: 0,
      devices: []
    }
  },
  servers: [
    {
      id: 'srv_de_1',
      name: 'Frankfurt VIP 01 - Fast',
      countryCode: 'DE',
      countryName: 'Germany',
      ipOrDomain: 'de1.ifixvpn.net',
      port: 443,
      protocol: 'VLESS',
      latencyMs: 38,
      status: 'ONLINE',
      userCapacityPercent: 32,
      flagEmoji: '🇩🇪'
    },
    {
      id: 'srv_nl_1',
      name: 'Amsterdam Streaming 01',
      countryCode: 'NL',
      countryName: 'Netherlands',
      ipOrDomain: 'nl1.ifixvpn.net',
      port: 8443,
      protocol: 'VMess',
      latencyMs: 45,
      status: 'ONLINE',
      userCapacityPercent: 58,
      flagEmoji: '🇳🇱'
    },
    {
      id: 'srv_fi_1',
      name: 'Helsinki Ultra Secure',
      countryCode: 'FI',
      countryName: 'Finland',
      ipOrDomain: 'fi1.ifixvpn.net',
      port: 2083,
      protocol: 'Trojan',
      latencyMs: 52,
      status: 'ONLINE',
      userCapacityPercent: 25,
      flagEmoji: '🇫🇮'
    },
    {
      id: 'srv_us_1',
      name: 'New York Gaming 01',
      countryCode: 'US',
      countryName: 'United States',
      ipOrDomain: 'us1.ifixvpn.net',
      port: 443,
      protocol: 'Xray',
      latencyMs: 110,
      status: 'ONLINE',
      userCapacityPercent: 70,
      flagEmoji: '🇺🇸'
    }
  ],
  remoteConfig: {
    announcementMessage: '⚡ Welcome to IFIX VPN! High speed commercial VIP servers are active with zero logging.',
    isForceUpdateRequired: false,
    latestVersionName: '1.0.0',
    latestVersionCode: 1,
    releaseNotes: 'Initial Release of IFIX VPN Client with real online license system.',
    telegramChannel: 'https://t.me/ifixvpn_official',
    supportUrl: 'https://ifixvpn.com/support'
  }
};

const ADMIN_SECRET = process.env.ADMIN_SECRET || 'ifix_admin_secret_2026';

function publicLicense(lic) {
  return {
    licenseKey: lic.licenseKey,
    status: lic.status,
    planType: lic.planType,
    expiryTimestamp: lic.expiryTimestamp,
    maxDevices: lic.maxDevices,
    activeDevicesCount: lic.devices.length
  };
}

function isExpired(lic) {
  return Date.now() > lic.expiryTimestamp;
}

// ── 1. Activate License ──────────────────────────────────────────────────────
app.post('/api/license/activate', (req, res) => {
  const { licenseKey, deviceId, deviceName } = req.body || {};

  if (!licenseKey || !deviceId) {
    return res.status(400).json({
      success: false,
      error: 'licenseKey and deviceId are required.'
    });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];

  if (!lic) {
    return res.status(404).json({
      success: false,
      error: 'License key not found. Please purchase a valid IFIX VPN license.'
    });
  }

  if (lic.status === 'DEACTIVATED') {
    return res.status(403).json({
      success: false,
      error: 'This license has been deactivated by the administrator.'
    });
  }

  if (isExpired(lic) || lic.status === 'EXPIRED') {
    lic.status = 'EXPIRED';
    return res.status(403).json({
      success: false,
      error: 'License key has expired. Please renew your subscription.'
    });
  }

  // Already bound to this device?
  const existingDevice = lic.devices.find((d) => d.deviceId === deviceId);
  if (existingDevice) {
    return res.json({ success: true, license: publicLicense(lic) });
  }

  // Max devices check
  if (lic.devices.length >= lic.maxDevices) {
    return res.status(403).json({
      success: false,
      error: `Device limit reached (${lic.maxDevices}). Deactivate another device first.`
    });
  }

  lic.devices.push({
    deviceId,
    deviceName: deviceName || 'Android',
    activatedAt: Date.now()
  });
  lic.activeDevicesCount = lic.devices.length;

  return res.json({ success: true, license: publicLicense(lic) });
});

// ── 2. Check License Status ──────────────────────────────────────────────────
app.post('/api/license/check', (req, res) => {
  const { licenseKey, deviceId } = req.body || {};
  if (!licenseKey) {
    return res.status(400).json({ success: false, status: 'INVALID', error: 'licenseKey required' });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];

  if (!lic) {
    return res.status(404).json({ success: false, status: 'INVALID', error: 'License not found' });
  }

  if (isExpired(lic)) {
    lic.status = 'EXPIRED';
    return res.status(403).json({ success: false, status: 'EXPIRED', error: 'License expired', license: publicLicense(lic) });
  }

  if (lic.status !== 'ACTIVE') {
    return res.status(403).json({ success: false, status: lic.status, error: 'License not active', license: publicLicense(lic) });
  }

  // Optional: verify device is still bound
  if (deviceId) {
    const bound = lic.devices.some((d) => d.deviceId === deviceId);
    if (!bound) {
      return res.status(403).json({
        success: false,
        status: 'DEVICE_NOT_BOUND',
        error: 'This device is not authorized for this license.'
      });
    }
  }

  return res.json({ success: true, status: 'ACTIVE', license: publicLicense(lic) });
});

// ── 3. Deactivate License (unbind current device) ────────────────────────────
app.post('/api/license/deactivate', (req, res) => {
  const { licenseKey, deviceId } = req.body || {};
  if (!licenseKey) {
    return res.status(400).json({ success: false, error: 'licenseKey required' });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];
  if (!lic) {
    return res.json({ success: true, message: 'License not found (already clear).' });
  }

  if (deviceId) {
    lic.devices = lic.devices.filter((d) => d.deviceId !== deviceId);
    lic.activeDevicesCount = lic.devices.length;
  } else {
    // Full deactivate if no deviceId
    lic.status = 'DEACTIVATED';
    lic.devices = [];
    lic.activeDevicesCount = 0;
  }

  return res.json({ success: true, message: 'License deactivated on this device.' });
});

// ── 4. Admin: Create new license key ─────────────────────────────────────────
app.post('/api/admin/license/create', (req, res) => {
  const secret = req.headers['x-admin-secret'] || req.body?.adminSecret;
  if (secret !== ADMIN_SECRET) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }

  const {
    planType = 'Commercial VIP Pass',
    daysValid = 365,
    maxDevices = 5,
    customKey
  } = req.body || {};

  const key =
    customKey?.trim().toUpperCase() ||
    `IFIX-${crypto.randomBytes(3).toString('hex').toUpperCase()}-${crypto.randomBytes(2).toString('hex').toUpperCase()}`;

  if (database.licenses[key]) {
    return res.status(409).json({ success: false, error: 'Key already exists' });
  }

  const lic = {
    licenseKey: key,
    status: 'ACTIVE',
    planType,
    expiryTimestamp: Date.now() + Number(daysValid) * 24 * 3600 * 1000,
    maxDevices: Number(maxDevices),
    activeDevicesCount: 0,
    devices: []
  };
  database.licenses[key] = lic;

  return res.json({ success: true, license: publicLicense(lic) });
});

// ── 5. Admin: List all licenses ──────────────────────────────────────────────
app.get('/api/admin/licenses', (req, res) => {
  const secret = req.headers['x-admin-secret'];
  if (secret !== ADMIN_SECRET) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }
  const list = Object.values(database.licenses).map(publicLicense);
  return res.json({ success: true, licenses: list });
});

// ── 6. Server list ───────────────────────────────────────────────────────────
app.get('/api/server/list', (req, res) => {
  res.json({ success: true, servers: database.servers });
});

// ── 7. Remote config ─────────────────────────────────────────────────────────
app.get('/api/config', (req, res) => {
  res.json({ success: true, config: database.remoteConfig });
});

// ── Health ───────────────────────────────────────────────────────────────────
app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'IFIX VPN License API', time: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 IFIX VPN Backend running on port ${PORT}`);
  console.log(`   Demo keys: IFIX-VIP-PRO-2026 | IFIX-PREMIUM-9999 | IFIX-DEMO-TEST`);
  console.log(`   Admin secret header: x-admin-secret`);
});
