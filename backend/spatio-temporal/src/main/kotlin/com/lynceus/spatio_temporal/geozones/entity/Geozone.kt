package com.lynceus.spatio_temporal.geozones.entity

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*

@Entity
@Table(name = "geozones")
@Schema(description = "Сущность геозоны")
data class Geozone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Уникальный идентификатор геозоны", example = "1")
    val id: Long? = null,

    @Column(name = "name", nullable = false)
    @Schema(description = "Название геозоны", example = "Центр Москвы")
    var name: String = "",

    @Column(name = "type", nullable = false, length = 10)
    @Schema(description = "Тип геозоны", example = "city")
    var type: String = "",

    @Column(name = "coordinates", nullable = false, columnDefinition = "jsonb")
    @Schema(description = "Координаты геозоны в формате JSON", example = "[[37.6173,55.7558],[37.6231,55.7512]]")
    var coordinates: String = "",

    @Column(name = "is_active", nullable = false)
    @Schema(description = "Флаг активности геозоны", example = "true")
    var isActive: Boolean = true,

    @Column(name = "s2_key", nullable = false)
    @Schema(description = "S2 ключ геозоны", example = "1234567890123456789")
    var s2Key: Long = 0,

    @Column(name = "lat", nullable = false)
    @Schema(description = "Широта центра геозоны", example = "55.7558")
    var lat: Double = 0.0,

    @Column(name = "lon", nullable = false)
    @Schema(description = "Долгота центра геозоны", example = "37.6173")
    var lon: Double = 0.0
)
