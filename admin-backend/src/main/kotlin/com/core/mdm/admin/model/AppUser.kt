package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "app_users")
data class AppUser(
    @Id
    val id: String = "",             // Firebase UID

    @Column(nullable = false, unique = true)
    val email: String = "",

    val displayName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.USER,

    val tenantId: String? = null,    // null for SUPER_ADMIN

    val createdAt: Instant = Instant.now(),

    var lastLoginAt: Instant? = null,
    var lastLoginIp: String? = null,
)
