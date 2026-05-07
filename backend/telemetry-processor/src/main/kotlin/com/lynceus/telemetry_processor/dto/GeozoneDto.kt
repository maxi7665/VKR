package com.lynceus.telemetry_processor.dto

import io.swagger.v3.oas.annotations.media.Schema

data class GeozoneDto(
    @Schema(description = "Идентификатор геозоны", example = "50606")
    val id: Long,

    @Schema(description = "Название геозоны", example = "АЗС №36/ г.Пушкин, Красносельское ш., д.64")
    val name: String,

    @Schema(description = "Тип геозоны (polygon, circle, etc.)", example = "polygon")
    val type: String,

    @Schema(description = "Координаты геозоны (список точек)")
    val coordinates: List<List<Double>>,

    @Schema(description = "Активна ли геозона", example = "true")
    val isActive: Boolean,

    @Schema(description = "S2 ключ геозоны", example = "5086289682816303104")
    val s2Key: Long,

    @Schema(description = "Широта центра геозоны", example = "59.703122022512396")
    val lat: Double,

    @Schema(description = "Долгота центра геозоны", example = "30.3586004702657")
    val lon: Double
)