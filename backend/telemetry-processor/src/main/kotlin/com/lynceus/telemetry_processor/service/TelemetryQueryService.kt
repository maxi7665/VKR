package com.lynceus.telemetry_processor.service

import com.google.common.geometry.S2LatLng
import com.google.common.geometry.S2Loop
import com.google.common.geometry.S2Polygon
import com.google.common.geometry.S2RegionCoverer
import com.lynceus.telemetry_processor.dto.DeviceTelemetryRequest
import com.lynceus.telemetry_processor.dto.PolygonTimeRequest
import com.lynceus.telemetry_processor.dto.TelemetryIntervalResponse
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.processor.NavigationProcessor
import com.lynceus.telemetry_processor.repository.TelemetryPacketRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TelemetryQueryService(
    private val telemetryPacketRepository: TelemetryPacketRepository
) {

    fun findTelemetryIntervals(request: PolygonTimeRequest): List<TelemetryIntervalResponse> {

        val points = request.polygon.map { S2LatLng.fromDegrees(
            it.latitude,
            it.longitude).toPoint() }

        val s2Loop = S2Loop(points)
        s2Loop.normalize()
        val s2Polygon = S2Polygon(s2Loop)

        if (!s2Polygon.isValid) {
            return emptyList()
        }

        val coverer = S2RegionCoverer.builder()
            .setMaxLevel(NavigationProcessor.S2_ZONE_LEVEL) // Максимальный уровень
            .setMinLevel(1) // Минимальный уровень (самые большие ячейки)
            .setMaxCells(200) // Больше ячеек для лучшего покрытия
            .build()

        // Получаем покрытие прямоугольника S2 ячейками разных уровней
        val cellUnion = coverer.getCovering(s2Polygon)

        val ranges = mutableListOf<Pair<Long, Long>>()

        for (i in 0 until cellUnion.size()) {
            val cell = cellUnion.cellId(i)

            // Получаем диапазон дочерних ячеек уровня 24
            val rangeStart = cell.childBegin(24).id()
            val rangeEnd = cell.childEnd(24).id() - 1 // -1 потому что childEnd исключительный

            ranges.add(Pair(rangeStart, rangeEnd))
        }

        // Сортируем по начальному ID
        ranges.sortBy { it.first }

        val mergedRanges = mergeAdjacentRanges(ranges)

        val foundDevices = telemetryPacketRepository.findByS2KeyInRanges(
            request.fromDateTime,
            request.toDateTime,
            mergedRanges
        )

        // Заглушка: возвращаем пустой список
        // Реализацию добавим позже
        return foundDevices
    }

    fun getDeviceTelemetryInPeriod(request: DeviceTelemetryRequest): List<TelemetryPacket> {
        return telemetryPacketRepository.getDeviceTelemetryInPeriod(
            request.deviceId,
            request.fromDateTime,
            request.toDateTime
        )
    }

    private fun mergeAdjacentRanges(sortedRanges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (sortedRanges.isEmpty()) {
            return emptyList()
        }

        val merged = mutableListOf<Pair<Long, Long>>()
        var currentStart = sortedRanges[0].first
        var currentEnd = sortedRanges[0].second

        for (i in 1 until sortedRanges.size) {
            val (nextStart, nextEnd) = sortedRanges[i]

            // Если интервалы пересекаются или соседние (разница в 1)
            if (nextStart <= currentEnd + 1) {
                // Объединяем
                currentEnd = maxOf(currentEnd, nextEnd)
            } else {
                // Сохраняем текущий и начинаем новый
                merged.add(Pair(currentStart, currentEnd))
                currentStart = nextStart
                currentEnd = nextEnd
            }
        }

        // Добавляем последний интервал
        merged.add(Pair(currentStart, currentEnd))

        return merged
    }
}