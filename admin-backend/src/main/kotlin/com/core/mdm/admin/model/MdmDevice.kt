package com.core.mdm.admin.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "mdm_devices")
data class MdmDevice(
    @Id
    val id: String = "",             // hardware ID (same as Firestore doc ID)

    @Column(nullable = false)
    val ownerId: String = "",        // Firebase UID of the enrolled user

    val tenantId: String? = null,

    val model: String = "",
    val manufacturer: String? = null,
    val osVersion: String = "",
    val imei: String? = null,
    val serial: String? = null,

    val enrolledAt: Instant = Instant.now(),
    var lastSeen: Instant? = null,

    var status: String = "offline",   // "online" | "offline"
    var alarmActive: Boolean = false,
)
