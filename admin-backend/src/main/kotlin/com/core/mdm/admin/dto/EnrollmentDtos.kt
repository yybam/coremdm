package com.core.mdm.admin.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// ── Pre-registration (admin → backend) ───────────────────────────────────────

data class PreRegisterRequest(
    @field:NotBlank(message = "serialNumber must not be blank")
    @field:Size(max = 128, message = "serialNumber exceeds 128 characters")
    val serialNumber: String,

    @field:NotBlank(message = "deviceIdentifier must not be blank")
    @field:Size(max = 255, message = "deviceIdentifier exceeds 255 characters")
    val deviceIdentifier: String,

    // Null = Wi-Fi-only device. Non-null must be exactly 15 digits (format check);
    // Luhn validity is enforced in EnrollmentService.
    @field:Pattern(
        regexp = "^\\d{15}$",
        message = "IMEI must be exactly 15 digits"
    )
    val imei: String? = null,

    @field:NotBlank(message = "tenantId must not be blank")
    val tenantId: String,
)

data class BulkPreRegisterRequest(
    @field:NotEmpty(message = "devices list must not be empty")
    val devices: List<@Valid PreRegisterRequest>,
)

data class PreRegisterResponse(
    val id: String,
    val serialNumber: String,
    val deviceIdentifier: String,
    val imei: String?,
    val tenantId: String,
    val enrollmentStatus: String,
    val enrollmentToken: String,            // Plaintext — shown once, never persisted
    val enrollmentTokenExpiresAt: String,   // ISO-8601
)

// ── Enrollment handshake (Android device → backend) ──────────────────────────

data class EnrollRequest(
    @field:NotBlank(message = "serialNumber must not be blank")
    val serialNumber: String,

    @field:NotBlank(message = "deviceIdentifier must not be blank")
    val deviceIdentifier: String,

    val imei: String? = null,

    @field:NotBlank(message = "enrollmentToken must not be blank")
    val enrollmentToken: String,
)

data class EnrollResponse(
    val deviceId: String,
    val tenantId: String,
    val deviceSessionToken: String,
    val enrolledAt: String,   // ISO-8601
)

// ── Inventory query response ──────────────────────────────────────────────────

data class InventoryDeviceDto(
    val id: String,
    val serialNumber: String,
    val deviceIdentifier: String,
    val imei: String?,
    val tenantId: String,
    val enrollmentStatus: String,
    val enrolledAt: String?,
    val lastSeenAt: String?,
    val hardwareId: String?,
    val preRegisteredBy: String?,
    val createdAt: String,
)
