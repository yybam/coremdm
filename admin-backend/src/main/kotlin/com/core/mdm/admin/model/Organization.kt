package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "organizations")
data class Organization(
    @Id
    val tenantId: String = "",

    @Column(nullable = false, unique = true)
    val name: String = "",

    val createdAt: Instant = Instant.now(),

    val contactEmail: String? = null,
)
