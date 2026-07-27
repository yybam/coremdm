package com.core.mdm.admin.service

import com.core.mdm.admin.dto.*
import com.core.mdm.admin.model.*
import com.core.mdm.admin.repository.DeviceSessionRepository
import com.core.mdm.admin.repository.ProvisionedDeviceRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class EnrollmentService(
    private val inventoryRepo: ProvisionedDeviceRepository,
    private val sessionRepo: DeviceSessionRepository,
    private val auditLogService: AuditLogService,
) {
    private val bcrypt = BCryptPasswordEncoder()
    private val random = SecureRandom()

    // ── Pre-registration ──────────────────────────────────────────────────────

    /**
     * Registers one or more expected devices before physical deployment.
     * Generates a one-time enrollment token per device, returned in plaintext once.
     * The token hash is stored; the plaintext is never persisted.
     *
     * Entire batch is wrapped in one transaction — any duplicate fails the whole batch.
     */
    @Transactional
    fun preRegister(
        requests: List<PreRegisterRequest>,
        adminUserId: String,
        ip: String,
    ): List<PreRegisterResponse> = requests.map { req ->
        val serial    = req.serialNumber.trim()
        val devId     = req.deviceIdentifier.trim()
        val imei      = req.imei?.trim()?.ifBlank { null }
        val tenantId  = req.tenantId.trim()

        // Duplicate guard before any insert
        if (inventoryRepo.existsBySerialNumber(serial))
            throw DuplicateDeviceException("serialNumber already registered: $serial")
        if (inventoryRepo.existsByDeviceIdentifier(devId))
            throw DuplicateDeviceException("deviceIdentifier already registered: $devId")
        imei?.let {
            if (inventoryRepo.existsByImei(it))
                throw DuplicateDeviceException("IMEI already registered: $it")
        }

        // Validate IMEI with Luhn algorithm when present
        imei?.let { validateImei(it) }

        val plainToken  = generateToken()
        val tokenHash   = bcrypt.encode(plainToken)
        val expiresAt   = Instant.now().plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS)

        val device = inventoryRepo.save(
            ProvisionedDevice(
                serialNumber             = serial,
                deviceIdentifier         = devId,
                imei                     = imei,
                tenantId                 = tenantId,
                enrollmentStatus         = EnrollmentStatus.PENDING,
                enrollmentTokenHash      = tokenHash,
                enrollmentTokenExpiresAt = expiresAt,
                preRegisteredBy          = adminUserId,
            )
        )

        auditLogService.log(
            userId     = adminUserId,
            userEmail  = null,
            tenantId   = tenantId,
            ipAddress  = ip,
            userAgent  = null,
            endpoint   = "/api/admin/devices/pre-register",
            httpMethod = "POST",
            statusCode = 201,
            action     = "DEVICE_PRE_REGISTERED",
        )

        PreRegisterResponse(
            id                       = device.id,
            serialNumber             = device.serialNumber,
            deviceIdentifier         = device.deviceIdentifier,
            imei                     = device.imei,
            tenantId                 = device.tenantId,
            enrollmentStatus         = device.enrollmentStatus.name,
            enrollmentToken          = plainToken,
            enrollmentTokenExpiresAt = expiresAt.toString(),
        )
    }

    // ── Inventory query ───────────────────────────────────────────────────────

    fun getInventory(
        callerRole: Role,
        callerTenantId: String?,
        filterTenantId: String?,
        filterStatus: EnrollmentStatus?,
        filterSerial: String?,
    ): List<InventoryDeviceDto> {
        // ORGANIZATION_ADMIN is always scoped to their own tenant
        val effectiveTenantId = if (callerRole == Role.SUPER_ADMIN) filterTenantId else callerTenantId

        val devices = when {
            filterSerial != null ->
                inventoryRepo.findBySerialNumberContainingIgnoreCase(filterSerial)
                    .let { if (effectiveTenantId != null) it.filter { d -> d.tenantId == effectiveTenantId } else it }

            effectiveTenantId != null && filterStatus != null ->
                inventoryRepo.findByTenantIdAndEnrollmentStatus(effectiveTenantId, filterStatus)

            effectiveTenantId != null ->
                inventoryRepo.findByTenantId(effectiveTenantId)

            filterStatus != null ->
                inventoryRepo.findByEnrollmentStatus(filterStatus)

            else ->
                inventoryRepo.findAll()
        }

        return devices.map { it.toDto() }
    }

    // ── Enrollment handshake ──────────────────────────────────────────────────

    /**
     * Called by the Android device at first boot. Three-step verification:
     *   1. Match hardware identifiers against the pre-registered inventory.
     *   2. Check the token has not expired.
     *   3. BCrypt-verify the token against the stored hash.
     *
     * On success: status → ENROLLED, token cleared, device session issued.
     * On failure: audit log written with UNAUTHORIZED_ENROLLMENT_ATTEMPT, 403 thrown.
     */
    @Transactional
    fun enroll(request: EnrollRequest, ip: String): EnrollResponse {
        val serial = request.serialNumber.trim()
        val devId  = request.deviceIdentifier.trim()
        val imei   = request.imei?.trim()?.ifBlank { null }

        // 1. Match by any identifying field
        val candidates = inventoryRepo.findByAnyIdentifier(serial = serial, imei = imei, deviceId = devId)

        // Accept only PENDING records — ENROLLED / BLOCKED / DISENROLLED are all rejected
        val device = candidates.firstOrNull { it.enrollmentStatus == EnrollmentStatus.PENDING }

        if (device == null) {
            logUnauthorizedEnrollment(ip, tenantId = null)
            throw EnrollmentException("No matching PENDING device found for the provided identifiers")
        }

        // 2. Token expiry
        val expiresAt = device.enrollmentTokenExpiresAt
        if (expiresAt == null || Instant.now().isAfter(expiresAt)) {
            logUnauthorizedEnrollment(ip, device.tenantId)
            throw EnrollmentException("Enrollment token has expired")
        }

        // 3. Constant-time token verification
        val tokenHash = device.enrollmentTokenHash
        if (tokenHash == null || !bcrypt.matches(request.enrollmentToken, tokenHash)) {
            logUnauthorizedEnrollment(ip, device.tenantId)
            throw EnrollmentException("Invalid enrollment token")
        }

        // 4. Transition to ENROLLED, burn the one-time token
        val now = Instant.now()
        device.enrollmentStatus         = EnrollmentStatus.ENROLLED
        device.enrolledAt               = now
        device.lastSeenAt               = now
        device.hardwareId               = devId
        device.enrollmentTokenHash      = null
        device.enrollmentTokenExpiresAt = null
        inventoryRepo.save(device)

        // 5. Issue a device session token valid for SESSION_TTL_DAYS
        val sessionToken = UUID.randomUUID().toString()
        sessionRepo.save(
            DeviceSession(
                deviceId     = device.id,
                sessionToken = sessionToken,
                expiresAt    = now.plus(SESSION_TTL_DAYS, ChronoUnit.DAYS),
            )
        )

        auditLogService.log(
            userId     = null,
            userEmail  = null,
            tenantId   = device.tenantId,
            ipAddress  = ip,
            userAgent  = null,
            endpoint   = "/api/v1/mdm/enroll",
            httpMethod = "POST",
            statusCode = 200,
            action     = "DEVICE_ENROLLED",
        )

        return EnrollResponse(
            deviceId           = device.id,
            tenantId           = device.tenantId,
            deviceSessionToken = sessionToken,
            enrolledAt         = now.toString(),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateToken(): String {
        val bytes = ByteArray(24)    // 192 bits → 32-char base64url
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Luhn algorithm check for IMEI. Catches transcription errors in the 15-digit string.
     * The format (exactly 15 digits) must already be verified by the DTO @Pattern.
     */
    private fun validateImei(imei: String) {
        var sum = 0
        for (i in imei.indices) {
            var digit = imei[i].digitToInt()
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        if (sum % 10 != 0)
            throw ImeiValidationException("IMEI $imei fails Luhn check — verify the number is correct")
    }

    private fun logUnauthorizedEnrollment(ip: String, tenantId: String?) {
        auditLogService.log(
            userId     = null,
            userEmail  = null,
            tenantId   = tenantId,
            ipAddress  = ip,
            userAgent  = null,
            endpoint   = "/api/v1/mdm/enroll",
            httpMethod = "POST",
            statusCode = 403,
            action     = "UNAUTHORIZED_ENROLLMENT_ATTEMPT",
        )
    }

    private fun ProvisionedDevice.toDto() = InventoryDeviceDto(
        id               = id,
        serialNumber     = serialNumber,
        deviceIdentifier = deviceIdentifier,
        imei             = imei,
        tenantId         = tenantId,
        enrollmentStatus = enrollmentStatus.name,
        enrolledAt       = enrolledAt?.toString(),
        lastSeenAt       = lastSeenAt?.toString(),
        hardwareId       = hardwareId,
        preRegisteredBy  = preRegisteredBy,
        createdAt        = createdAt.toString(),
    )

    companion object {
        private const val TOKEN_TTL_HOURS  = 72L
        private const val SESSION_TTL_DAYS = 365L
    }
}

class EnrollmentException(message: String)         : RuntimeException(message)
class DuplicateDeviceException(message: String)    : RuntimeException(message)
class ImeiValidationException(message: String)     : RuntimeException(message)
