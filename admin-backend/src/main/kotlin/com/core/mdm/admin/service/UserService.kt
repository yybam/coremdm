package com.core.mdm.admin.service

import com.core.mdm.admin.model.AppUser
import com.core.mdm.admin.model.Role
import com.core.mdm.admin.repository.AuditLogRepository
import com.core.mdm.admin.repository.DeviceRepository
import com.core.mdm.admin.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class UserSummary(
    val id: String,
    val email: String,
    val displayName: String?,
    val role: String,
    val tenantId: String?,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    val lastLoginIp: String?,
    val enrolledDeviceCount: Int,
    val recentLoginCount: Int,
)

@Service
class UserService(
    private val userRepo: UserRepository,
    private val deviceRepo: DeviceRepository,
    private val auditRepo: AuditLogRepository,
) {

    fun findOrCreate(
        uid: String,
        email: String,
        displayName: String?,
        tenantId: String?,
    ): AppUser {
        return userRepo.findById(uid).orElseGet {
            userRepo.save(AppUser(
                id          = uid,
                email       = email,
                displayName = displayName,
                tenantId    = tenantId,
                role        = Role.USER,
            ))
        }
    }

    @Transactional
    fun recordLogin(uid: String, ip: String?) {
        userRepo.findById(uid).ifPresent { user ->
            userRepo.save(user.copy(lastLoginAt = Instant.now(), lastLoginIp = ip))
        }
    }

    fun getAllForAdmin(callerRole: Role, callerTenantId: String?): List<UserSummary> {
        val users = if (callerRole == Role.SUPER_ADMIN) {
            userRepo.findAll()
        } else {
            userRepo.findByTenantId(callerTenantId ?: return emptyList())
        }
        return users.map { u ->
            UserSummary(
                id                 = u.id,
                email              = u.email,
                displayName        = u.displayName,
                role               = u.role.name,
                tenantId           = u.tenantId,
                createdAt          = u.createdAt,
                lastLoginAt        = u.lastLoginAt,
                lastLoginIp        = u.lastLoginIp,
                enrolledDeviceCount = deviceRepo.findByOwnerId(u.id).size,
                recentLoginCount   = auditRepo.findByUserIdOrderByTimestampDesc(u.id, PageRequest.of(0, 100))
                    .count { it.action == "LOGIN" },
            )
        }
    }

    fun promoteToSuperAdmin(uid: String): AppUser {
        val user = userRepo.findById(uid).orElseThrow { NoSuchElementException("User $uid not found") }
        return userRepo.save(user.copy(role = Role.SUPER_ADMIN, tenantId = null))
    }

    fun setRole(uid: String, role: Role, tenantId: String?): AppUser {
        val user = userRepo.findById(uid).orElseThrow { NoSuchElementException("User $uid not found") }
        return userRepo.save(user.copy(role = role, tenantId = tenantId))
    }
}
