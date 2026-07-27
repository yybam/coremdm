-- CORE MDM Admin Backend — Database Schema
-- Compatible with H2 (dev) and PostgreSQL (prod).
-- In prod, run this once manually or via a migration tool (Flyway / Liquibase).

CREATE TABLE IF NOT EXISTS organizations (
    tenant_id     VARCHAR(128)  NOT NULL PRIMARY KEY,
    name          VARCHAR(255)  NOT NULL UNIQUE,
    contact_email VARCHAR(255),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_users (
    id            VARCHAR(128)  NOT NULL PRIMARY KEY,  -- Firebase UID
    email         VARCHAR(255)  NOT NULL UNIQUE,
    display_name  VARCHAR(255),
    role          VARCHAR(32)   NOT NULL DEFAULT 'USER',  -- SUPER_ADMIN | ORGANIZATION_ADMIN | USER
    tenant_id     VARCHAR(128),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(64),

    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES organizations (tenant_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mdm_devices (
    id           VARCHAR(255)  NOT NULL PRIMARY KEY,  -- hardware ID (Android ID / serial)
    owner_id     VARCHAR(128)  NOT NULL,
    tenant_id    VARCHAR(128),
    model        VARCHAR(255)  NOT NULL DEFAULT '',
    manufacturer VARCHAR(255),
    os_version   VARCHAR(64)   NOT NULL DEFAULT '',
    imei         VARCHAR(64),
    serial       VARCHAR(128),
    enrolled_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen    TIMESTAMP,
    status       VARCHAR(16)   NOT NULL DEFAULT 'offline',
    alarm_active BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_devices_owner  FOREIGN KEY (owner_id)  REFERENCES app_users     (id)        ON DELETE CASCADE,
    CONSTRAINT fk_devices_tenant FOREIGN KEY (tenant_id) REFERENCES organizations (tenant_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    user_id     VARCHAR(128),
    user_email  VARCHAR(255),
    tenant_id   VARCHAR(128),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    endpoint    VARCHAR(512)  NOT NULL,
    http_method VARCHAR(10)   NOT NULL,
    action      VARCHAR(64),
    status_code INT,
    timestamp   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user   ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_ip     ON audit_logs (ip_address);
CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_time   ON audit_logs (timestamp);

-- ── Device inventory (pre-provisioning records) ──────────────────────────────
-- Populated by admins before physical deployment. Tracks the full enrollment
-- lifecycle from PENDING through ENROLLED / DISENROLLED / BLOCKED.
CREATE TABLE IF NOT EXISTS device_inventory (
    id                            VARCHAR(36)   NOT NULL PRIMARY KEY,
    serial_number                 VARCHAR(128)  NOT NULL UNIQUE,
    device_identifier             VARCHAR(255)  NOT NULL UNIQUE,
    imei                          VARCHAR(15)   UNIQUE,            -- NULL for Wi-Fi-only
    tenant_id                     VARCHAR(128)  NOT NULL,
    enrollment_status             VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    enrollment_token_hash         VARCHAR(255),                    -- BCrypt; NULL after use
    enrollment_token_expires_at   TIMESTAMP,
    enrolled_at                   TIMESTAMP,
    last_seen_at                  TIMESTAMP,
    hardware_id                   VARCHAR(255),                    -- Android ID reported at enrollment
    pre_registered_by             VARCHAR(128),                    -- Firebase UID of registering admin
    created_at                    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_tenant FOREIGN KEY (tenant_id) REFERENCES organizations (tenant_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_inv_tenant  ON device_inventory (tenant_id);
CREATE INDEX IF NOT EXISTS idx_inv_status  ON device_inventory (enrollment_status);
CREATE INDEX IF NOT EXISTS idx_inv_hw_id   ON device_inventory (hardware_id);

-- ── Device sessions (issued after successful enrollment) ─────────────────────
-- One session per enrollment. Revoked on disenrollment.
CREATE TABLE IF NOT EXISTS device_sessions (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    device_id      VARCHAR(36)   NOT NULL,     -- References device_inventory.id
    session_token  VARCHAR(36)   NOT NULL UNIQUE,
    issued_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     TIMESTAMP,
    revoked        BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_session_device FOREIGN KEY (device_id) REFERENCES device_inventory (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_session_token    ON device_sessions (session_token);
CREATE INDEX IF NOT EXISTS idx_session_device   ON device_sessions (device_id);
