package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "device_sessions",
    indexes = [Index(name = "idx_session_token", columnList = "session_token", unique = true)]
)
data class DeviceSession(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // References ProvisionedDevice.id (no FK to avoid cascading complexity at runtime)
    @Column(name = "device_id", nullable = false)
    val deviceId: String = "",

    @Column(name = "session_token", nullable = false, unique = true)
    val sessionToken: String = UUID.randomUUID().toString(),

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    // Null means non-expiring; enforcement is caller-side
    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "revoked")
    val revoked: Boolean = false,
)
