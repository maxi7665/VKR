package com.lynceus.telemetry_processor.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class PolygonTimeRequest(
    @Schema(description = "Список точек, определяющих полигон (минимум 3 точки)")
    val polygon: List<PointDto>,

    @Schema(description = "Начало временного интервала", example = "2024-01-01T00:00:00")
    val fromDateTime: LocalDateTime,

    @Schema(description = "Конец временного интервала", example = "2024-01-02T23:59:59")
    val toDateTime: LocalDateTime
)