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
