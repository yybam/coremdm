package com.core.mdm.admin.service

import com.core.mdm.admin.model.MdmDevice
import com.core.mdm.admin.model.Role
import com.core.mdm.admin.repository.DeviceRepository
import com.core.mdm.admin.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

data class DeviceSummary(
    val id: String,
    val model: String,
    val manufacturer: String?,
    val osVersion: String,
    val imei: String?,
    val serial: String?,
    val ownerId: String,
    val ownerEmail: String?,
    val tenantId: String?,
    val status: String,
    val alarmActive: Boolean,
    val enrolledAt: Instant,
    val lastSeen: Instant?,
)

@Service
class DeviceService(
    private val deviceRepo: DeviceRepository,
    private val userRepo: UserRepository,
) {

    fun syncFromFirestore(
        id: String,
        ownerId: String,
        tenantId: String?,
        model: String,
        manufacturer: String?,
        osVersion: String,
        imei: String?,
        serial: String?,
        status: String,
        alarmActive: Boolean,
        lastSeen: Instant?,
    ): MdmDevice {
        val existing = deviceRepo.findById(id).orElse(null)
        val device = MdmDevice(
            id           = id,
            ownerId      = ownerId,
            tenantId     = tenantId,
            model        = model,
            manufacturer = manufacturer,
            osVersion    = osVersion,
            imei         = imei,
            serial       = serial,
            enrolledAt   = existing?.enrolledAt ?: Instant.now(),
            lastSeen     = lastSeen,
            status       = status,
            alarmActive  = alarmActive,
        )
        return deviceRepo.save(device)
    }

    fun getAllForAdmin(callerRole: Role, callerTenantId: String?): List<DeviceSummary> {
        val devices = if (callerRole == Role.SUPER_ADMIN) {
            deviceRepo.findAll()
        } else {
            deviceRepo.findByTenantId(callerTenantId ?: return emptyList())
        }
        val userCache = mutableMapOf<String, String?>()
        return devices.map { d ->
            DeviceSummary(
                id          = d.id,
                model       = d.model,
                manufacturer = d.manufacturer,
                osVersion   = d.osVersion,
                imei        = d.imei,
                serial      = d.serial,
                ownerId     = d.ownerId,
                ownerEmail  = userCache.getOrPut(d.ownerId) { userRepo.findById(d.ownerId).orElse(null)?.email },
                tenantId    = d.tenantId,
                status      = d.status,
                alarmActive = d.alarmActive,
                enrolledAt  = d.enrolledAt,
                lastSeen    = d.lastSeen,
            )
        }
    }
}
