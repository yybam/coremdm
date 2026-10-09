package com.core.mdm.admin.repository

import com.core.mdm.admin.model.AuditLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, String> {
    fun findByUserIdOrderByTimestampDesc(userId: String, pageable: Pageable): List<AuditLog>
    fun findByIpAddressOrderByTimestampDesc(ipAddress: String, pageable: Pageable): List<AuditLog>
    fun findByTenantIdOrderByTimestampDesc(tenantId: String, pageable: Pageable): List<AuditLog>
    fun findAllByOrderByTimestampDesc(pageable: Pageable): List<AuditLog>
}
