package com.lynceus.telemetry_processor.event

import java.time.LocalDateTime

class ZoneVisitEvent (
    val inOut: InOut,
    val deviceId: Long,
    val vehicleId: Long,
    val zoneId: Long,
    val zoneName: String,
    val zoneDateTime: LocalDateTime
)

enum class InOut {
    In,
    Out
}