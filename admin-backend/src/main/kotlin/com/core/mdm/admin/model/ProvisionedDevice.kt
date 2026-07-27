package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "device_inventory",
    indexes = [
        Index(name = "idx_inv_tenant",    columnList = "tenant_id"),
        Index(name = "idx_inv_status",    columnList = "enrollment_status"),
        Index(name = "idx_inv_hw_id",     columnList = "hardware_id"),
    ]
)
data class ProvisionedDevice(
    @Id
    val id: String = UUID.randomUUID().toString(),

    // The three hardware identifiers used for matching at enrollment time.
    // At least one must be unique across the inventory.
    @Column(name = "serial_number", nullable = false, unique = true)
    val serialNumber: String = "",

    @Column(name = "device_identifier", nullable = false, unique = true)
    val deviceIdentifier: String = "",

    @Column(name = "imei", unique = true)
    val imei: String? = null,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false)
    var enrollmentStatus: EnrollmentStatus = EnrollmentStatus.PENDING,

    // BCrypt hash of the one-time token. Never returned after creation.
    @Column(name = "enrollment_token_hash")
    var enrollmentTokenHash: String? = null,

    @Column(name = "enrollment_token_expires_at")
    var enrollmentTokenExpiresAt: Instant? = null,

    @Column(name = "enrolled_at")
    var enrolledAt: Instant? = null,

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,

    // Populated after enrollment: the Android hardware ID the device reported
    @Column(name = "hardware_id")
    var hardwareId: String? = null,

    @Column(name = "pre_registered_by")
    val preRegisteredBy: String? = null,   // Firebase UID of the admin who created the record

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
