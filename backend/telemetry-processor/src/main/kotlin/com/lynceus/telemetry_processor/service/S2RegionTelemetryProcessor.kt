package com.lynceus.telemetry_processor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class S2RegionTelemetryProcessor(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val navigationDuration = Duration.ofDays(1)

    // s2key региона -> Set<deviceId>
    private val s2keyToDeviceIdSet = hashMapOf<Long, MutableSet<Long>>()

    // deviceId -> s2key где находится ТС
    private val deviceIdToS2Key = hashMapOf<Long, Long>()

    @PostConstruct
    fun loadCellsDevices() {
        val pattern = "cell*"
        val options: ScanOptions = ScanOptions
            .scanOptions().match(pattern).build()
        val cursor: Cursor<String> =  redisTemplate.scan(options)

        val setOps = redisTemplate.opsForSet()
        var added = 0

        // Iterate over the cursor
        while (cursor.hasNext()) {
            val key = cursor.next()
            val cellId = key.split(":").getOrNull(1)?.toLongOrNull()

            if (cellId != null) {
                val set = s2keyToDeviceIdSet.computeIfAbsent(cellId) {hashSetOf()}
                val members = setOps.members(key)
                if (members != null) {
                    for (member in members) {
                        val deviceId = member.toString().toLongOrNull()

                        if (deviceId != null) {
                            set.add(deviceId)
                            added ++
                            deviceIdToS2Key[deviceId] = cellId
                        }
                    }
                }
            }
        }

        logger.info("loaded $added cell's devices")
    }

    @Synchronized
    fun processPacket(telemetryPacket: TelemetryPacket) {
        val oldS2 = deviceIdToS2Key[telemetryPacket.deviceId]
        val newS2 = telemetryPacket.s2Cell

        // добавили ли в новую область устройство
        val newAdded = s2keyToDeviceIdSet
            .computeIfAbsent(newS2) {hashSetOf()}
                .add(telemetryPacket.deviceId)

        // удалили ли из старой области устройство
        val oldDeleted = oldS2
            ?.let { s2keyToDeviceIdSet[oldS2]
                ?.remove(telemetryPacket.deviceId) } ?: false

        val ops = redisTemplate.opsForSet()

        // удаляем ТС из старой области
        if (oldS2 != null) {
            ops.remove(
                "cell:${oldS2}:devices",
                telemetryPacket.deviceId.toString())
        }

        // добавляем ТС в новую область
        ops.add(
            "cell:$newS2:devices",
            telemetryPacket.deviceId.toString())

        val navKey = "nav:${telemetryPacket.deviceId}:last"
        val data = objectMapper.writeValueAsString(telemetryPacket)

        // устанавливаем последние данные по ключу (это триггерит keyspace notification)
        redisTemplate.opsForValue().set(
            navKey,
            data,
            navigationDuration)

        deviceIdToS2Key[telemetryPacket.deviceId] = newS2
    }
}