package com.lynceus.telemetry_processor.controller

import com.lynceus.telemetry_processor.dto.DeviceTelemetryRequest
import com.lynceus.telemetry_processor.dto.PolygonTimeRequest
import com.lynceus.telemetry_processor.dto.TelemetryIntervalResponse
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.service.TelemetryQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry", description = "API для запросов телеметрии")
class TelemetryController(
    private val telemetryQueryService: TelemetryQueryService
) {

    @PostMapping("/query-by-polygon")
    @Operation(
        summary = "Запрос телеметрии по полигону и временному интервалу",
        description = "Возвращает интервалы нахождения транспортных средств внутри заданного полигона за указанный период времени"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Успешный ответ",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = TelemetryIntervalResponse::class, type = "array")
        )]
    )
    @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
    fun queryByPolygon(@RequestBody request: PolygonTimeRequest): List<TelemetryIntervalResponse> {
        return telemetryQueryService.findTelemetryIntervals(request)
    }

    @PostMapping("/device-telemetry")
    @Operation(
        summary = "Запрос телеметрии устройства за период",
        description = "Возвращает все пакеты телеметрии для указанного устройства в заданном временном интервале"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Успешный ответ",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = TelemetryPacket::class, type = "array")
        )]
    )
    @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
    fun getDeviceTelemetry(@RequestBody request: DeviceTelemetryRequest): List<TelemetryPacket> {
        return telemetryQueryService.getDeviceTelemetryInPeriod(request)
    }

}