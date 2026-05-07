package com.lynceus.telemetry_processor.dto

import io.swagger.v3.oas.annotations.media.Schema

data class PointDto(
    @Schema(description = "Широта в градусах", example = "55.7558")
    val latitude: Double,

    @Schema(description = "Долгота в градусах", example = "37.6173")
    val longitude: Double
)