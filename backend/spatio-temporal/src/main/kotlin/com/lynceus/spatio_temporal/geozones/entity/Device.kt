package com.lynceus.spatio_temporal.geozones.entity

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*

@Entity
@Table(name = "devices")
@Schema(description = "Сущность устройства")
data class Device(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Уникальный идентификатор устройства", example = "1")
    val id: Long? = null,

    @Column(name = "name", nullable = false)
    @Schema(description = "Название устройства", example = "Автотранспорт №1")
    var name: String = "",

    @Column(name = "registration_number", nullable = false)
    @Schema(description = "Регистрационный номер", example = "А123АА777")
    var registrationNumber: String = "",

    @Column(name = "device_id", nullable = false)
    @Schema(description = "Уникальный ID устройства", example = "DEV001234567")
    var deviceId: String = "",

    @Column(name = "type_id", nullable = false)
    @Schema(description = "Тип устройства", example = "1")
    var typeId: Int = 0,

    @Column(name = "department_id")
    @Schema(description = "ID подразделения", example = "1")
    var departmentId: Int? = null,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp default now()")
    @Schema(description = "Дата создания устройства", example = "2026-01-15T10:30:00")
    var createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)
