package com.lynceus.telemetry_processor.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/**
 * Событие изменения состава устройств в S2 ячейке.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellUpdateEvent(
    val cellId: Long,
    val deviceId: Long,
    val action: String, // "ADD" или "REMOVE"
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Событие обновления телеметрии устройства.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelemetryUpdateEvent(
    val deviceId: Long,
    val vehicleId: Long,
    val latitude: Double,
    val longitude: Double,
    val s2Cell: Long,
    val azimuth: Short,
    val packetTime: LocalDateTime,
    val timestamp: LocalDateTime = LocalDateTime.now()
)