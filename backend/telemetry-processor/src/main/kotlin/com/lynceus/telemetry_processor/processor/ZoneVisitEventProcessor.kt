package com.lynceus.telemetry_processor.processor

import com.google.common.geometry.*
import com.lynceus.telemetry_processor.dto.GeozoneDto
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.event.InOut
import com.lynceus.telemetry_processor.event.ZoneVisitEvent
import com.lynceus.telemetry_processor.service.GeozoneService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ZoneVisitEventProcessor(
    private val geozoneService: GeozoneService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val redisTemplate: RedisTemplate<String, String>,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val query: S2ContainsPointQuery
    private val zoneIdToZones: Map<Long, GeozoneDto>
    private val shapeToZonesList: Map<S2Shape, List<GeozoneDto>>
    private val deviceToCurrentZoneIdSet = hashMapOf<Long, HashSet<Long>>()

    init {
        // все зоны грузим в пространственный индекс
        val zones = geozoneService.getAllGeozones()
        val shapeToZones = hashMapOf<S2Shape, MutableList<GeozoneDto>>()

        zoneIdToZones = zones.associateBy { it.id }

        val index = S2ShapeIndex()

        for (zone in zones) {
            val points = zone.coordinates.map{ S2LatLng.fromDegrees(
                it[0],
                it[1]
            ).toPoint() }

            val polygon = S2Polygon(S2Loop(points).normalize())

            if (polygon.isValid) {
                val shape = polygon.shape()

                index.add(shape)

                shapeToZones.computeIfAbsent(shape) { mutableListOf() }
                    .add(zone)
            }
        }

        val q = S2ContainsPointQuery(index)

        query = q
        shapeToZonesList = shapeToZones
    }

    fun processPacket(packet: TelemetryPacket) {

        val point = S2LatLng.fromDegrees(
            packet.latitude,
            packet.longitude).toPoint()
        val shapes = query.getContainingShapes(point)
        val oldZoneIds = deviceToCurrentZoneIdSet.computeIfAbsent(
            packet.deviceId){
            hashSetOf()
        }
        var newZoneIds: HashSet<Long>? = null

        for (shape in shapes) {
            val zones = shapeToZonesList[shape]
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

        for (addedZoneId in addedZoneIds) {
            val zone = zoneIdToZones[addedZoneId]

            if (zone != null) {
                publishEvent(zone, packet, InOut.In)
            }
        }

        for (removedZoneId in removedZoneIds) {
            val zone = zoneIdToZones[removedZoneId]

            if (zone != null) {
                publishEvent(zone, packet, InOut.Out)
            }
        }



        // TODO сюда выгрузка в redis
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