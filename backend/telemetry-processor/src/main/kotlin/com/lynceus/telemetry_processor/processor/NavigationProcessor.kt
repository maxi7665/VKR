package com.lynceus.telemetry_processor.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.repository.TelemetryPacketRepository
import com.lynceus.telemetry_processor.service.S2RegionTelemetryProcessor
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@Component
class NavigationProcessor(
    private val telemetryPacketRepository: TelemetryPacketRepository,
    private val objectMapper: ObjectMapper,
    private val redisTelemetryStorage: S2RegionTelemetryProcessor,
    private val zoneVisitEventProcessor: ZoneVisitEventProcessor
) {
    
    private val logger = LoggerFactory.getLogger(NavigationProcessor::class.java)
    
    private val buffer = mutableListOf<TelemetryPacket>()
    private val bufferLock = ReentrantLock()

    companion object {
        /**
         * Кол-во данных, при накоплении которых они сохраняются в БД
         */
        private const val BATCH_SIZE = 100

        /**
         * Уровень S2-ключа для простраственной индексации телеметрических данных
         */
        const val S2_ZONE_LEVEL = 14
    }
    
    fun process(message: ByteArray) {
        try {
            val jsonString = String(message, Charsets.UTF_8)
            logger.debug("Parsing JSON: $jsonString")
            
            val rootNode = objectMapper.readTree(jsonString)
            
            val vehicleIdNode = rootNode["num_garage"]
            val deviceIdNode = rootNode["id_obj"]
            val packetTimeNode = rootNode["date_real"]
            val receptionTimeNode = rootNode["date_turn"]
            val latitudeNode = rootNode["latitude"]
            val longitudeNode = rootNode["longitude"]
            val azimuthNode = rootNode["course"]
            
            if (vehicleIdNode == null) {
                logger.error("Missing 'num_garage' field in message")
                return
            }
            
            val vehicleId = when {
                vehicleIdNode.isTextual -> vehicleIdNode.asText().toLongOrNull()
                vehicleIdNode.isNumber -> vehicleIdNode.asLong()
                else -> {
                    logger.error("Invalid 'num_garage' field type: ${vehicleIdNode.nodeType}")
                    return
                }
            }
            
            if (vehicleId == null) {
                logger.error("Invalid numeric format for 'num_garage': ${vehicleIdNode.asText()}")
                return
            }
            
            if (deviceIdNode == null || !deviceIdNode.isNumber) {
                logger.error("Missing or invalid 'id_obj' field in message")
                return
            }
            val deviceId = deviceIdNode.asLong()
            
            if (packetTimeNode == null || !packetTimeNode.isNumber) {
                logger.error("Missing or invalid 'date_real' field in message")
                return
            }
            val packetTimeEpoch = packetTimeNode.asLong()
            
            if (receptionTimeNode == null || !receptionTimeNode.isNumber) {
                logger.error("Missing or invalid 'date_turn' field in message")
                return
            }
            val receptionTimeEpoch = receptionTimeNode.asLong()
            
            if (latitudeNode == null || !latitudeNode.isNumber) {
                logger.error("Missing or invalid 'latitude' field in message")
                return
            }
            val latitude = latitudeNode.asDouble()
            
            if (longitudeNode == null || !longitudeNode.isNumber) {
                logger.error("Missing or invalid 'longitude' field in message")
                return
            }
            val longitude = longitudeNode.asDouble()
            
            val packetTime = Instant.ofEpochMilli(packetTimeEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            val receptionTime = Instant.ofEpochMilli(receptionTimeEpoch)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()

            // пространственный индекс определенной мощности по точке
            val s2cell = S2CellId.fromLatLng(
                S2LatLng.fromDegrees(
                    latitude,
                    longitude))
                .parent(S2_ZONE_LEVEL)


            // 1. Truncate to the nearest minute (removes seconds and nanos)
            val truncated: LocalDateTime = packetTime.truncatedTo(ChronoUnit.MINUTES)

            // 2. Calculate the remainder and subtract it from the current minute
            val minute = truncated.minute
            val minuteAdjustment = minute % 5

            val fiveMinTruncated = truncated.minusMinutes(minuteAdjustment.toLong())
            
            val telemetryPacket = TelemetryPacket(
                vehicleId = vehicleId,
                deviceId = deviceId,
                packetTime = packetTime,
                receptionTime = receptionTime,
                latitude = latitude,
                longitude = longitude,
                s2Cell = s2cell.id(),
                azimuth = azimuthNode.asInt().toShort(),
                discretizedPackedTime = fiveMinTruncated
            )
            
            logger.debug("Created TelemetryPacket: vehicleId=$vehicleId, deviceId=$deviceId, lat=$latitude, lon=$longitude")
            addToBuffer(telemetryPacket)
            
        } catch (e: Exception) {
            logger.error("Failed to process navigation message: ${e.message}", e)
            e.printStackTrace() // temporary for debugging
        }
    }
    
    private fun addToBuffer(packet: TelemetryPacket) {
        bufferLock.withLock {
            //logger.debug("Adding telemetry packet to buffer: vehicleId=${packet.vehicleId}, deviceId=${packet.deviceId}")
            buffer.add(packet)
            redisTelemetryStorage.processPacket(packet)
            zoneVisitEventProcessor.processPacket(packet)
            if (buffer.size >= BATCH_SIZE) {
                //logger.info("Buffer size reached $BATCH_SIZE, flushing")
                flushBuffer()
            }
        }
    }
    
    private fun flushBuffer() {
        bufferLock.withLock {
            if (buffer.isEmpty()) return
            
            try {
                telemetryPacketRepository.saveAll(buffer)
                //logger.info("Saved ${buffer.size} telemetry packets to database")

//                for (packet in buffer) {
//                    // Сохраняем те же пакеты в Redis
//                    redisTelemetryStorage.processPacket(packet)
//                }
                //logger.info("Saved ${buffer.size} telemetry packets to Redis")
                
                buffer.clear()
            } catch (e: Exception) {
                logger.error("Failed to save telemetry packets batch", e)
            }
        }
    }
    
    @PreDestroy
    fun onDestroy() {
        logger.info("Flushing remaining telemetry packets before shutdown")
        flushRemaining()
    }
    
    fun flushRemaining() {
        flushBuffer()
    }
}