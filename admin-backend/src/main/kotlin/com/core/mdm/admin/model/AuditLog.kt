package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "audit_logs", indexes = [
    Index(name = "idx_audit_user",   columnList = "userId"),
    Index(name = "idx_audit_ip",     columnList = "ipAddress"),
    Index(name = "idx_audit_tenant", columnList = "tenantId"),
    Index(name = "idx_audit_time",   columnList = "timestamp"),
])
data class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: String = "",

    val userId: String? = null,
    val userEmail: String? = null,
    val tenantId: String? = null,

    val ipAddress: String? = null,
    val userAgent: String? = null,

    @Column(nullable = false)
    val endpoint: String = "",

    @Column(nullable = false, length = 10)
    val httpMethod: String = "",

    val action: String? = null,       // e.g. "LOGIN", "POLICY_CHANGE", "WIPE"
    val statusCode: Int? = null,

    @Column(nullable = false)
    val timestamp: Instant = Instant.now(),
)
