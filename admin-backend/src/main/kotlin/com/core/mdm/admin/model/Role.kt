package com.core.mdm.admin.model

sealed class Permission {
    data object GlobalRead    : Permission()
    data object GlobalWrite   : Permission()
    data object TenantRead    : Permission()
    data object TenantWrite   : Permission()
    data object AuditRead     : Permission()
}

enum class Role(val permissions: Set<Permission>) {
    SUPER_ADMIN(setOf(
        Permission.GlobalRead, Permission.GlobalWrite,
        Permission.TenantRead, Permission.TenantWrite,
        Permission.AuditRead,
    )),
    ORGANIZATION_ADMIN(setOf(
        Permission.TenantRead, Permission.TenantWrite,
    )),
    USER(setOf(
        Permission.TenantRead,
    ));

    fun hasPermission(permission: Permission) = permission in permissions
    fun isAtLeast(other: Role) = ordinal <= other.ordinal
}
