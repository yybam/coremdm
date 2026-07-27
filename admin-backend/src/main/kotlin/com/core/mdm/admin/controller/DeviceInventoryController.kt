package com.core.mdm.admin.controller

import com.core.mdm.admin.dto.BulkPreRegisterRequest
import com.core.mdm.admin.dto.InventoryDeviceDto
import com.core.mdm.admin.dto.PreRegisterRequest
import com.core.mdm.admin.dto.PreRegisterResponse
import com.core.mdm.admin.model.EnrollmentStatus
import com.core.mdm.admin.model.Role
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_TENANT_ID
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_USER_ID
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_USER_ROLE
import com.core.mdm.admin.service.EnrollmentService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/devices")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZATION_ADMIN')")
class DeviceInventoryController(private val enrollmentService: EnrollmentService) {

    // ── POST /api/admin/devices/pre-register (JSON — single or bulk) ──────────
    @PostMapping("/pre-register")
    fun preRegister(
        @Valid @RequestBody body: BulkPreRegisterRequest,
        request: HttpServletRequest,
    ): ResponseEntity<List<PreRegisterResponse>> {
        val ctx    = callerContext(request)
        val result = enrollmentService.preRegister(body.devices, ctx.adminId, ctx.ip)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    // ── POST /api/admin/devices/pre-register/single (convenience single-item) ─
    @PostMapping("/pre-register/single")
    fun preRegisterSingle(
        @Valid @RequestBody body: PreRegisterRequest,
        request: HttpServletRequest,
    ): ResponseEntity<PreRegisterResponse> {
        val ctx    = callerContext(request)
        val result = enrollmentService.preRegister(listOf(body), ctx.adminId, ctx.ip)
        return ResponseEntity.status(HttpStatus.CREATED).body(result.first())
    }

    // ── POST /api/admin/devices/pre-register/csv (CSV file upload) ────────────
    // Expected CSV format — header row optional:
    //   serialNumber,deviceIdentifier,imei,tenantId
    //   SN001,uuid-1,123456789012345,tenant-a
    //   SN002,uuid-2,,tenant-a          ← blank IMEI = Wi-Fi-only
    @PostMapping("/pre-register/csv", consumes = ["multipart/form-data"])
    fun preRegisterCsv(
        @RequestParam("file") file: MultipartFile,
        request: HttpServletRequest,
    ): ResponseEntity<List<PreRegisterResponse>> {
        if (file.isEmpty) return ResponseEntity.badRequest().build()
        val content  = file.inputStream.bufferedReader().readText()
        val requests = parseCsv(content)
        val ctx      = callerContext(request)
        val result   = enrollmentService.preRegister(requests, ctx.adminId, ctx.ip)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    // ── GET /api/admin/devices/inventory ──────────────────────────────────────
    // Query params: status (PENDING/ENROLLED/DISENROLLED/BLOCKED), tenantId, serial
    @GetMapping("/inventory")
    fun inventory(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(required = false) serial: String?,
        request: HttpServletRequest,
    ): ResponseEntity<List<InventoryDeviceDto>> {
        val ctx = callerContext(request)
        val enumStatus = status?.let {
            runCatching { EnrollmentStatus.valueOf(it.uppercase()) }.getOrElse {
                return ResponseEntity.badRequest().build()
            }
        }
        val result = enrollmentService.getInventory(
            callerRole       = ctx.role,
            callerTenantId   = ctx.tenantId,
            filterTenantId   = tenantId,
            filterStatus     = enumStatus,
            filterSerial     = serial,
        )
        return ResponseEntity.ok(result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class CallerCtx(
        val adminId:  String,
        val role:     Role,
        val ip:       String,
        val tenantId: String?,
    )

    private fun callerContext(request: HttpServletRequest): CallerCtx {
        val uid      = request.getAttribute(ATTR_USER_ID) as? String ?: error("No authenticated user")
        val roleName = request.getAttribute(ATTR_USER_ROLE) as? String ?: "USER"
        val role     = runCatching { Role.valueOf(roleName) }.getOrElse { Role.USER }
        val tenantId = request.getAttribute(ATTR_TENANT_ID) as? String
        val ip       = com.core.mdm.admin.security.FirebaseTokenFilter.extractIp(request)
        return CallerCtx(uid, role, ip, tenantId)
    }

    // ── CSV parser ────────────────────────────────────────────────────────────

    private fun parseCsv(content: String): List<PreRegisterRequest> {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) throw IllegalArgumentException("CSV file is empty")

        // Skip header row if it starts with a non-digit (e.g. "serialNumber")
        val dataLines = if (lines.first().firstOrNull()?.isLetter() == true) lines.drop(1) else lines

        if (dataLines.isEmpty()) throw IllegalArgumentException("CSV contains only a header row — no data")

        return dataLines.mapIndexed { idx, line ->
            val cols = splitCsvLine(line)
            if (cols.size < 4) throw IllegalArgumentException(
                "CSV row ${idx + 2}: expected 4 columns (serialNumber,deviceIdentifier,imei,tenantId), got ${cols.size}"
            )
            PreRegisterRequest(
                serialNumber     = cols[0],
                deviceIdentifier = cols[1],
                imei             = cols[2].ifBlank { null },
                tenantId         = cols[3],
            )
        }
    }

    // Handles quoted fields containing commas; trims surrounding whitespace and quotes
    private fun splitCsvLine(line: String): List<String> {
        val result  = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        for (ch in line) {
            when {
                ch == '"'            -> inQuote = !inQuote
                ch == ',' && !inQuote -> { result.add(current.toString().trim()); current.clear() }
                else                 -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
