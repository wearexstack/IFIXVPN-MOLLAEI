/**
 * IFIX VPN REST API – Real License System
 * - Only pre-registered keys can be activated
 * - Device binding (maxDevices = 1 per license)
 * - Expiry check (30 days)
 * - Admin endpoint to create new licenses
 */

const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

const ONE_MONTH_MS = 30 * 24 * 3600 * 1000;

function makeLicense(key) {
  return {
    licenseKey: key,
    status: 'ACTIVE',
    planType: 'اشتراک یک‌ماهه نامحدود',
    expiryTimestamp: Date.now() + ONE_MONTH_MS,
    maxDevices: 1,
    activeDevicesCount: 0,
    devices: []
  };
}

// ── In-memory store (replace with PostgreSQL in production) ──────────────────
const database = {
  licenses: {
    'IFIX-A7K2-M9P4': makeLicense('IFIX-A7K2-M9P4'),
    'IFIX-B3N8-Q1R6': makeLicense('IFIX-B3N8-Q1R6'),
    'IFIX-C5W2-T4Y9': makeLicense('IFIX-C5W2-T4Y9'),
    'IFIX-D8H1-U6V3': makeLicense('IFIX-D8H1-U6V3'),
    'IFIX-E2J7-X9Z4': makeLicense('IFIX-E2J7-X9Z4'),
    'IFIX-F4L0-A1B8': makeLicense('IFIX-F4L0-A1B8'),
    'IFIX-G6M3-C5D2': makeLicense('IFIX-G6M3-C5D2'),
    'IFIX-H9P5-E7F1': makeLicense('IFIX-H9P5-E7F1'),
    'IFIX-J1R8-G3H6': makeLicense('IFIX-J1R8-G3H6'),
    'IFIX-K3T4-J0L9': makeLicense('IFIX-K3T4-J0L9')
  },
  servers: [],
  remoteConfig: {
    announcementMessage: 'به IFIX VPN خوش آمدید. سرورهای تجاری با سرعت بالا فعال هستند.',
    isForceUpdateRequired: false,
    latestVersionName: '1.0.0',
    latestVersionCode: 1,
    releaseNotes: 'نسخه اولیه کلاینت IFIX VPN با سیستم لایسنس آنلاین.',
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
      error: 'کلید لایسنس و شناسه دستگاه الزامی است.'
    });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];

  if (!lic) {
    return res.status(404).json({
      success: false,
      error: 'کلید لایسنس یافت نشد. لطفاً یک لایسنس معتبر IFIX VPN تهیه کنید.'
    });
  }

  if (lic.status === 'DEACTIVATED') {
    return res.status(403).json({
      success: false,
      error: 'این لایسنس توسط مدیر غیرفعال شده است.'
    });
  }

  if (isExpired(lic) || lic.status === 'EXPIRED') {
    lic.status = 'EXPIRED';
    return res.status(403).json({
      success: false,
      error: 'لایسنس منقضی شده است. لطفاً اشتراک خود را تمدید کنید.'
    });
  }

  // Already bound to this device?
  const existingDevice = lic.devices.find((d) => d.deviceId === deviceId);
  if (existingDevice) {
    return res.json({ success: true, license: publicLicense(lic) });
  }

  // Max devices check (1 user / 1 device)
  if (lic.devices.length >= lic.maxDevices) {
    return res.status(403).json({
      success: false,
      error: `محدودیت دستگاه پر شده است (${lic.maxDevices}). ابتدا دستگاه قبلی را غیرفعال کنید.`
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
    return res.status(400).json({ success: false, status: 'INVALID', error: 'کلید لایسنس الزامی است' });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];

  if (!lic) {
    return res.status(404).json({ success: false, status: 'INVALID', error: 'لایسنس یافت نشد' });
  }

  if (isExpired(lic)) {
    lic.status = 'EXPIRED';
    return res.status(403).json({ success: false, status: 'EXPIRED', error: 'لایسنس منقضی شده', license: publicLicense(lic) });
  }

  if (lic.status !== 'ACTIVE') {
    return res.status(403).json({ success: false, status: lic.status, error: 'لایسنس فعال نیست', license: publicLicense(lic) });
  }

  if (deviceId) {
    const bound = lic.devices.some((d) => d.deviceId === deviceId);
    if (!bound) {
      return res.status(403).json({
        success: false,
        status: 'DEVICE_NOT_BOUND',
        error: 'این دستگاه برای این لایسنس مجاز نیست.'
      });
    }
  }

  return res.json({ success: true, status: 'ACTIVE', license: publicLicense(lic) });
});

// ── 3. Deactivate License (unbind current device) ────────────────────────────
app.post('/api/license/deactivate', (req, res) => {
  const { licenseKey, deviceId } = req.body || {};
  if (!licenseKey) {
    return res.status(400).json({ success: false, error: 'کلید لایسنس الزامی است' });
  }

  const cleanKey = String(licenseKey).trim().toUpperCase();
  const lic = database.licenses[cleanKey];
  if (!lic) {
    return res.json({ success: true, message: 'لایسنس یافت نشد.' });
  }

  if (deviceId) {
    lic.devices = lic.devices.filter((d) => d.deviceId !== deviceId);
    lic.activeDevicesCount = lic.devices.length;
  } else {
    lic.status = 'DEACTIVATED';
    lic.devices = [];
    lic.activeDevicesCount = 0;
  }

  return res.json({ success: true, message: 'لایسنس روی این دستگاه غیرفعال شد.' });
});

// ── 4. Admin: Create new license key ─────────────────────────────────────────
app.post('/api/admin/license/create', (req, res) => {
  const secret = req.headers['x-admin-secret'] || req.body?.adminSecret;
  if (secret !== ADMIN_SECRET) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }

  const {
    planType = 'اشتراک یک‌ماهه نامحدود',
    daysValid = 30,
    maxDevices = 1,
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
  console.log(`   Licenses loaded: ${Object.keys(database.licenses).length}`);
  console.log(`   Admin secret header: x-admin-secret`);
});
