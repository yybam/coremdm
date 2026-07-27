package com.core.mdm.admin.repository

import com.core.mdm.admin.model.EnrollmentStatus
import com.core.mdm.admin.model.ProvisionedDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProvisionedDeviceRepository : JpaRepository<ProvisionedDevice, String> {

    fun findBySerialNumber(serialNumber: String): ProvisionedDevice?
    fun findByDeviceIdentifier(deviceIdentifier: String): ProvisionedDevice?
    fun findByImei(imei: String): ProvisionedDevice?

    fun existsBySerialNumber(serialNumber: String): Boolean
    fun existsByDeviceIdentifier(deviceIdentifier: String): Boolean
    fun existsByImei(imei: String): Boolean

    fun findByTenantId(tenantId: String): List<ProvisionedDevice>
    fun findByEnrollmentStatus(status: EnrollmentStatus): List<ProvisionedDevice>
    fun findByTenantIdAndEnrollmentStatus(tenantId: String, status: EnrollmentStatus): List<ProvisionedDevice>
    fun findBySerialNumberContainingIgnoreCase(fragment: String): List<ProvisionedDevice>

    // Match an enrollment request against any of the three identifying columns.
    // The JPQL :imei IS NULL guard prevents false matches when the caller provides no IMEI.
    @Query("""
        SELECT d FROM ProvisionedDevice d
        WHERE d.serialNumber = :serial
           OR (:imei IS NOT NULL AND d.imei = :imei)
           OR d.deviceIdentifier = :deviceId
    """)
    fun findByAnyIdentifier(serial: String, imei: String?, deviceId: String): List<ProvisionedDevice>
}
