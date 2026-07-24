package com.core.mdm.firebase

data class DeviceInfo(
    val id: String,
    val name: String,
    val osVersion: String,
    val alarmActive: Boolean,
    val lastSeen: Long?,
)
