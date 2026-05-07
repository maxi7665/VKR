package com.lynceus.spatio_temporal.geozones

import com.lynceus.spatio_temporal.geozones.entity.Device

class DeviceDto(
    val id: Long? = null,
    val name: String = "",
    val registrationNumber: String = "",
    val deviceId: String = "",
    val typeId: Int = 0,
    val departmentId: Int? = null,
    val createdAt: java.time.LocalDateTime? = null
) {
    companion object {
        fun fromDevice(device: Device): DeviceDto {
            return DeviceDto(
                id = device.id,
                name = device.name,
                registrationNumber = device.registrationNumber,
                deviceId = device.deviceId,
                typeId = device.typeId,
                departmentId = device.departmentId,
                createdAt = device.createdAt
            )
        }
    }
}
