package com.lynceus.telemetry_processor.service

import com.lynceus.telemetry_processor.entity.TelemetryPacket
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZoneId

@Component
class RedisTelemetryStorage(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(RedisTelemetryStorage::class.java)

    companion object {
        private const val KEY_SEPARATOR = ":"

        val expires = Duration.ofDays(1)
    }

    /**
     * Сохраняет пакет телеметрии в Redis.
     * Ключ формируется как "s2cell:vehicleId:deviceId:packetTimeUnix"
     * Значение - сериализованный объект TelemetryPacket.
     */
    fun save(packet: TelemetryPacket) {
        try {
            val key = buildKey(packet)
            redisTemplate.opsForValue().set(key, packet)
            logger.debug("Saved telemetry packet to Redis with key: $key")
        } catch (e: Exception) {
            logger.error("Failed to save telemetry packet to Redis: ${e.message}", e)
        }
    }

    /**
     * Сохраняет список пакетов телеметрии в Redis.
     * Использует pipeline для эффективной пакетной записи.
     */
    fun saveAll(packets: List<TelemetryPacket>) {
        if (packets.isEmpty()) return

        try {
            val operations = redisTemplate.opsForValue()
            redisTemplate.executePipelined { connection ->
                packets.forEach { packet ->
                    val key = buildKey(packet)
                    operations.set(key, packet, expires)
                }
                null
            }
            //logger.debug("Saved ${packets.size} telemetry packets to Redis via pipeline")
        } catch (e: Exception) {
            logger.error("Failed to save telemetry packets batch to Redis: ${e.message}", e)
        }
    }

    /**
     * Формирует ключ Redis в формате "s2cell:vehicleId:deviceId:packetTimeUnix"
     */
    private fun buildKey(packet: TelemetryPacket): String {
        val packetTimeUnix = packet.packetTime.atZone(ZoneId.systemDefault()).toEpochSecond()
        return "nav$KEY_SEPARATOR${packet.vehicleId}$KEY_SEPARATOR${packet.s2Cell}$KEY_SEPARATOR${packet.deviceId}$KEY_SEPARATOR$packetTimeUnix"
    }

    /**
     * Получает пакет телеметрии по его ключевым параметрам.
     */
    fun get(s2Cell: Long, vehicleId: Long, deviceId: Long, packetTimeUnix: Long): TelemetryPacket? {
        val key = "${s2Cell}$KEY_SEPARATOR$vehicleId$KEY_SEPARATOR$deviceId$KEY_SEPARATOR$packetTimeUnix"
        return redisTemplate.opsForValue().get(key) as? TelemetryPacket
    }

    /**
     * Удаляет пакет телеметрии из Redis.
     */
    fun delete(s2Cell: Long, vehicleId: Long, deviceId: Long, packetTimeUnix: Long): Boolean {
        val key = "${s2Cell}$KEY_SEPARATOR$vehicleId$KEY_SEPARATOR$deviceId$KEY_SEPARATOR$packetTimeUnix"
        return redisTemplate.delete(key) ?: false
    }

    /**
     * Проверяет наличие пакета в Redis.
     */
    fun exists(s2Cell: Long, vehicleId: Long, deviceId: Long, packetTimeUnix: Long): Boolean {
        val key = "${s2Cell}$KEY_SEPARATOR$vehicleId$KEY_SEPARATOR$deviceId$KEY_SEPARATOR$packetTimeUnix"
        return redisTemplate.hasKey(key) ?: false
    }
}