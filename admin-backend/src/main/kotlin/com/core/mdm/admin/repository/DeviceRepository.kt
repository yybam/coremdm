package com.core.mdm.admin.repository

import com.core.mdm.admin.model.MdmDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DeviceRepository : JpaRepository<MdmDevice, String> {
    fun findByOwnerId(ownerId: String): List<MdmDevice>
    fun findByTenantId(tenantId: String): List<MdmDevice>

    @Query("SELECT d FROM MdmDevice d WHERE d.tenantId = :tenantId OR :isSuperAdmin = true")
    fun findByTenantIdOrAll(tenantId: String?, isSuperAdmin: Boolean): List<MdmDevice>
}
