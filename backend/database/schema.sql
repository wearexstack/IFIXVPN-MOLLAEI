-- IFIX VPN Database Schema (PostgreSQL / MySQL compatible)

CREATE TABLE IF NOT EXISTS licenses (
    id SERIAL PRIMARY KEY,
    license_key VARCHAR(64) UNIQUE NOT NULL,
    plan_type VARCHAR(50) DEFAULT 'VIP Commercial',
    status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, DEACTIVATED
    max_devices INT DEFAULT 5,
    active_devices_count INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    license_key VARCHAR(64) REFERENCES licenses(license_key),
    last_ip VARCHAR(45),
    last_connected_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vpn_servers (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    country_code VARCHAR(10) NOT NULL,
    country_name VARCHAR(50) NOT NULL,
    ip_or_domain VARCHAR(100) NOT NULL,
    port INT NOT NULL,
    protocol VARCHAR(20) NOT NULL, -- VLESS, VMess, Trojan, Shadowsocks, Xray
    latency_ms INT DEFAULT 50,
    status VARCHAR(20) DEFAULT 'ONLINE',
    user_capacity_percent INT DEFAULT 30,
    flag_emoji VARCHAR(10) NOT NULL,
    config_raw_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS remote_configs (
    id SERIAL PRIMARY KEY,
    announcement_message TEXT,
    is_force_update_required BOOLEAN DEFAULT FALSE,
    latest_version_name VARCHAR(20) DEFAULT '1.0.0',
    latest_version_code INT DEFAULT 1,
    release_notes TEXT,
    telegram_channel VARCHAR(100) DEFAULT 'https://t.me/ifixvpn_official',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Initial Mock Data
INSERT INTO licenses (license_key, plan_type, status, max_devices, expires_at)
VALUES ('IFIX-VIP-PRO-2026', 'VIP 1-Year Commercial Pass', 'ACTIVE', 5, CURRENT_TIMESTAMP + INTERVAL '365 days')
ON CONFLICT (license_key) DO NOTHING;

INSERT INTO remote_configs (announcement_message, is_force_update_required, latest_version_name, latest_version_code, release_notes)
VALUES ('⚡ Welcome to IFIX VPN! High speed commercial VIP servers are online.', FALSE, '1.0.0', 1, 'Initial Release of IFIX VPN Client')
ON CONFLICT DO NOTHING;
