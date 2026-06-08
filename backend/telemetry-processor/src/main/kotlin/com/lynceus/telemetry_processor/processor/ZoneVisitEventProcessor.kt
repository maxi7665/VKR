package com.lynceus.telemetry_processor.processor

import com.google.common.geometry.*
import com.lynceus.telemetry_processor.dto.GeozoneDto
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.event.InOut
import com.lynceus.telemetry_processor.event.ZoneVisitEvent
import com.lynceus.telemetry_processor.service.GeozoneService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ZoneVisitEventProcessor(
    private val geozoneService: GeozoneService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val zoneIdToZones: Map<Long, GeozoneDto>
    private val deviceToCurrentZoneIdSet = hashMapOf<Long, HashSet<Long>>()
    private val zoneIndex: S2ZoneIndex
    private val zones: List<GeozoneDto>

    init {
        // все зоны грузим в пространственный индекс
        zones = geozoneService.getAllGeozones()
        val shapeToZones = hashMapOf<S2Shape, MutableList<GeozoneDto>>()

        zoneIdToZones = zones.associateBy { it.id }

        var validCount = 0
        var totalCount = 0
        val regions = mutableListOf<S2Region>()

        for (zone in zones) {
            totalCount++
            val points = zone.coordinates.map{ S2LatLng.fromDegrees(
                it[0],
                it[1]
            ).toPoint() }

            val loop = S2Loop(points)
            loop.normalize()
            
            // Check loop validity
            if (!loop.isValid) {
                println("DEBUG: Loop is invalid for zone ${zone.id}")
                continue
            }
            
            val polygon = S2Polygon(loop)

            if (polygon.isValid) {
                validCount++
                regions.add(polygon)
            } else {
                // Polygon is invalid, skip it
                println("DEBUG: Polygon is invalid for zone ${zone.id}, loop valid=${loop.isValid}")
            }
        }

        // Debug output
        println("ZoneVisitEventProcessor init: $validCount/$totalCount polygons valid")

        zoneIndex = S2ZoneIndex(
            regions = regions,
            coverer = S2RegionCoverer.builder()
                .setMaxLevel(24)
                .setMinLevel(1)
                .setMaxCells(200)
                .build(),
            targetLevel = 24
        )
    }

    fun processPacket(packet: TelemetryPacket) {

        val point = S2LatLng.fromDegrees(
            packet.latitude,
            packet.longitude).toPoint()
        val regions = zoneIndex.findRegions(point)
        val oldZoneIds = deviceToCurrentZoneIdSet.computeIfAbsent(
            packet.deviceId){
            hashSetOf()
        }
        var newZoneIds: HashSet<Long>? = null
        val zones = regions.map { zones[it] }

        for (region in zones) {

            if (zones != null) {
                newZoneIds = newZoneIds ?: hashSetOf()
                for (zone in zones) {
                    newZoneIds.add(zone.id)
                }
            }
        }

        val newZones = newZoneIds ?: emptySet()

        val addedZoneIds = newZones.subtract(oldZoneIds)
        val removedZoneIds = oldZoneIds.subtract(newZones)

        // вход в зону
        for (addedZoneId in addedZoneIds) {
            val zone = zoneIdToZones[addedZoneId]

            if (zone != null) {
                publishEvent(zone, packet, InOut.In)
            }
        }

        // выход из зоны
        for (removedZoneId in removedZoneIds) {
            val zone = zoneIdToZones[removedZoneId]

            if (zone != null) {
                publishEvent(zone, packet, InOut.Out)
            }
        }

        if (newZoneIds != null) {
            deviceToCurrentZoneIdSet[packet.deviceId] = newZoneIds
        }
        else {
            deviceToCurrentZoneIdSet.remove(packet.deviceId)
        }
    }

    fun publishEvent(zone: GeozoneDto, packet: TelemetryPacket, inOut: InOut) {
        val event = ZoneVisitEvent(
            inOut = inOut,
            deviceId = packet.deviceId,
            vehicleId = packet.vehicleId,
            zoneId = zone.id,
            zoneName = zone.name,
            zoneDateTime = packet.packetTime
        )
        applicationEventPublisher.publishEvent(event)
    }
}