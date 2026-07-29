/**
 * IFIX VPN REST API & Remote Config Express Server
 */

const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

// In-Memory Database Store for quick testing & demonstration
const database = {
  licenses: {
    'IFIX-VIP-PRO-2026': {
      licenseKey: 'IFIX-VIP-PRO-2026',
      status: 'ACTIVE',
      planType: 'VIP 1-Year Commercial Pass',
      expiryTimestamp: Date.now() + 365 * 24 * 3600 * 1000,
      maxDevices: 5,
      activeDevicesCount: 1
    },
    'IFIX-PREMIUM-9999': {
      licenseKey: 'IFIX-PREMIUM-9999',
      status: 'ACTIVE',
      planType: 'Ultra High-Speed Pass',
      expiryTimestamp: Date.now() + 180 * 24 * 3600 * 1000,
      maxDevices: 5,
      activeDevicesCount: 2
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
    }
  ],
  remoteConfig: {
    announcementMessage: '⚡ Welcome to IFIX VPN! High speed commercial VIP servers are active with zero logging.',
    isForceUpdateRequired: false,
    latestVersionName: '1.0.0',
    latestVersionCode: 1,
    releaseNotes: 'Initial Release of IFIX VPN Client',
    telegramChannel: 'https://t.me/ifixvpn_official',
    supportUrl: 'https://ifixvpn.com/support'
  }
};

// --- REST API ENDPOINTS ---

// 1. Activate License
app.post('/api/license/activate', (req, res) => {
  const { licenseKey } = req.body;
  if (!licenseKey) {
    return res.status(400).json({ success: false, error: 'License key is required.' });
  }

  const cleanKey = licenseKey.trim().toUpperCase();
  const existing = database.licenses[cleanKey];

  if (existing) {
    if (existing.status !== 'ACTIVE') {
      return res.status(403).json({ success: false, error: 'License key is expired or deactivated.' });
    }
    return res.json({ success: true, license: existing });
  }

  // Dynamic Auto-Registration for new valid keys
  const newLicense = {
    licenseKey: cleanKey,
    status: 'ACTIVE',
    planType: 'Commercial VIP Pass',
    expiryTimestamp: Date.now() + 365 * 24 * 3600 * 1000,
    maxDevices: 5,
    activeDevicesCount: 1
  };
  database.licenses[cleanKey] = newLicense;

  return res.json({ success: true, license: newLicense });
});

// 2. Check License Status
app.post('/api/license/check', (req, res) => {
  const { licenseKey } = req.body;
  const existing = database.licenses[licenseKey];
  if (!existing) {
    return res.status(444).json({ success: false, status: 'INVALID' });
  }
  res.json({ success: true, license: existing });
});

// 3. Deactivate License
app.post('/api/license/deactivate', (req, res) => {
  const { licenseKey } = req.body;
  if (database.licenses[licenseKey]) {
    database.licenses[licenseKey].status = 'DEACTIVATED';
  }
  res.json({ success: true, message: 'License deactivated.' });
});

// 4. Get Server List
app.get('/api/server/list', (req, res) => {
  res.json({ success: true, servers: database.servers });
});

// 5. Remote Config
app.get('/api/config', (req, res) => {
  res.json({ success: true, config: database.remoteConfig });
});

// Start Server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 IFIX VPN Backend Server running on port ${PORT}`);
});
