package com.core.mdm.admin.repository

import com.core.mdm.admin.model.DeviceSession
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceSessionRepository : JpaRepository<DeviceSession, String> {
    fun findBySessionToken(sessionToken: String): DeviceSession?
    fun findByDeviceId(deviceId: String): List<DeviceSession>
}
