package com.core.mdm.admin.service

import com.core.mdm.admin.model.AuditLog
import com.core.mdm.admin.repository.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuditLogService(private val repo: AuditLogRepository) {

    fun log(
        userId: String?,
        userEmail: String?,
        tenantId: String?,
        ipAddress: String?,
        userAgent: String?,
        endpoint: String,
        httpMethod: String,
        statusCode: Int?,
        action: String? = null,
    ) {
        repo.save(AuditLog(
            userId     = userId,
            userEmail  = userEmail,
            tenantId   = tenantId,
            ipAddress  = ipAddress,
            userAgent  = userAgent,
            endpoint   = endpoint,
            httpMethod = httpMethod,
            statusCode = statusCode,
            action     = action,
            timestamp  = Instant.now(),
        ))
    }

    fun getAll(page: Int, size: Int) =
        repo.findAllByOrderByTimestampDesc(PageRequest.of(page, size))

    fun getByUserId(userId: String, page: Int, size: Int) =
        repo.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(page, size))

    fun getByIp(ip: String, page: Int, size: Int) =
        repo.findByIpAddressOrderByTimestampDesc(ip, PageRequest.of(page, size))

    fun getByTenant(tenantId: String, page: Int, size: Int) =
        repo.findByTenantIdOrderByTimestampDesc(tenantId, PageRequest.of(page, size))
}
