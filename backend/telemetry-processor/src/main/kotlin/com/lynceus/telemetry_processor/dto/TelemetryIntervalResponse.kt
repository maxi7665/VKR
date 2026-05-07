package com.lynceus.telemetry_processor.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class TelemetryIntervalResponse(
    @Schema(description = "Идентификатор транспортного средства", example = "12345")
    val vehicleId: Long,

    @Schema(description = "Идентификатор устройства", example = "67890")
    val deviceId: Long,

    @Schema(description = "Начало интервала нахождения в полигоне", example = "2024-01-01T10:30:00")
    val fromDateTime: LocalDateTime,

    @Schema(description = "Конец интервала нахождения в полигоне", example = "2024-01-01T11:45:00")
    val toDateTime: LocalDateTime
)