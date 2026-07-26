package com.core.mdm.admin.controller

import com.core.mdm.admin.model.AuditLog
import com.core.mdm.admin.model.Role
import com.core.mdm.admin.repository.UserRepository
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_TENANT_ID
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_USER_ID
import com.core.mdm.admin.security.FirebaseTokenFilter.Companion.ATTR_USER_ROLE
import com.core.mdm.admin.service.AuditLogService
import com.core.mdm.admin.service.DeviceService
import com.core.mdm.admin.service.DeviceSummary
import com.core.mdm.admin.service.UserService
import com.core.mdm.admin.service.UserSummary
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AdminController(
    private val userService: UserService,
    private val deviceService: DeviceService,
    private val auditLogService: AuditLogService,
    private val userRepository: UserRepository,
) {

    // ── GET /api/admin/users ─────────────────────────────────────────────────
    // Lists all registered profiles across the entire system.
    // Returns: email, role, tenant, last login IP, device count, login history count.
    @GetMapping("/users")
    fun listUsers(request: HttpServletRequest): ResponseEntity<List<UserSummary>> {
        val (role, tenantId) = callerContext(request)
        return ResponseEntity.ok(userService.getAllForAdmin(role, tenantId))
    }

    // ── GET /api/admin/devices ───────────────────────────────────────────────
    // Lists all MDM devices across the entire system with owner + telemetry.
    @GetMapping("/devices")
    fun listDevices(request: HttpServletRequest): ResponseEntity<List<DeviceSummary>> {
        val (role, tenantId) = callerContext(request)
        return ResponseEntity.ok(deviceService.getAllForAdmin(role, tenantId))
    }

    // ── GET /api/admin/audit-logs ────────────────────────────────────────────
    // Historical login events and user actions. Filterable by userId or IP.
    @GetMapping("/audit-logs")
    fun listAuditLogs(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) ip: String?,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<List<AuditLog>> {
        val logs = when {
            userId   != null -> auditLogService.getByUserId(userId, page, size)
            ip       != null -> auditLogService.getByIp(ip, page, size)
            tenantId != null -> auditLogService.getByTenant(tenantId, page, size)
            else             -> auditLogService.getAll(page, size)
        }
        return ResponseEntity.ok(logs)
    }

    // ── POST /api/admin/users/{uid}/role ─────────────────────────────────────
    // Elevate or demote a user's role. SUPER_ADMIN only.
    @PostMapping("/users/{uid}/role")
    fun setUserRole(
        @PathVariable uid: String,
        @RequestBody body: RoleChangeRequest,
    ): ResponseEntity<Any> {
        val role = runCatching { Role.valueOf(body.role) }.getOrElse {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid role: ${body.role}"))
        }
        val updated = userService.setRole(uid, role, body.tenantId)
        return ResponseEntity.ok(mapOf(
            "id"       to updated.id,
            "email"    to updated.email,
            "role"     to updated.role.name,
            "tenantId" to updated.tenantId,
        ))
    }

    // ── GET /api/admin/health ────────────────────────────────────────────────
    @GetMapping("/health")
    fun health() = ResponseEntity.ok(mapOf("status" to "ok", "role" to "SUPER_ADMIN"))

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun callerContext(request: HttpServletRequest): Pair<Role, String?> {
        val uid      = request.getAttribute(ATTR_USER_ID) as? String ?: error("No user")
        val tenantId = request.getAttribute(ATTR_TENANT_ID) as? String
        val roleName = request.getAttribute(ATTR_USER_ROLE) as? String ?: "USER"
        val role     = runCatching { Role.valueOf(roleName) }.getOrElse { Role.USER }
        return role to tenantId
    }
}

data class RoleChangeRequest(
    val role: String,
    val tenantId: String? = null,
)
