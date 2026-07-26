package com.core.mdm.admin.repository

import com.core.mdm.admin.model.AppUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<AppUser, String> {
    fun findByEmail(email: String): AppUser?
    fun findByTenantId(tenantId: String): List<AppUser>

    @Query("SELECT u FROM AppUser u WHERE u.tenantId = :tenantId OR :isSuperAdmin = true")
    fun findByTenantIdOrAll(tenantId: String?, isSuperAdmin: Boolean): List<AppUser>
}
