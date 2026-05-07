# Изменения для S2RegionTelemetryProcessor.kt

## Обзор
Удаление публикации событий через Redis pub/sub и переход на keyspace notifications.

## Текущее состояние
Класс `S2RegionTelemetryProcessor` содержит:
1. Методы `publishCellUpdate()` и `publishTelemetryUpdate()`
2. Константы `CHANNEL_CELL_UPDATES` и `CHANNEL_TELEMETRY_UPDATES`
3. Вызовы `redisTemplate.convertAndSend()` для публикации событий

## Целевое состояние
1. Удалить все публикации событий
2. Сохранить только операции с Redis (`cell:*:devices` и `nav:*:last`)
3. Оставить логику обновления s2Cell и deviceId маппингов

## Детальные изменения

### 1. Удалить импорты событий
```kotlin
// УДАЛИТЬ:
import com.lynceus.telemetry_processor.event.CellUpdateEvent
import com.lynceus.telemetry_processor.event.TelemetryUpdateEvent
```

### 2. Удалить константы каналов
```kotlin
// УДАЛИТЬ из companion object:
companion object {
    const val CHANNEL_CELL_UPDATES = "cell.updates"
    const val CHANNEL_TELEMETRY_UPDATES = "nav.updates"
}
```

### 3. Удалить методы публикации
```kotlin
// УДАЛИТЬ полностью методы:
private fun publishCellUpdate(cellId: Long, deviceId: Long, action: String)
private fun publishTelemetryUpdate(packet: TelemetryPacket)
```

### 4. Удалить вызовы публикации в `processPacket()`
```kotlin
// В методе processPacket() УДАЛИТЬ:
// 1. Вызов publishCellUpdate() для старой ячейки:
// publishCellUpdate(oldS2, telemetryPacket.deviceId, "REMOVE")

// 2. Вызов publishCellUpdate() для новой ячейки:
// publishCellUpdate(newS2, telemetryPacket.deviceId, "ADD")

// 3. Вызов publishTelemetryUpdate():
// publishTelemetryUpdate(telemetryPacket)
```

### 5. Обновленный метод `processPacket()`
```kotlin
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
        // УДАЛЕНО: publishCellUpdate(oldS2, telemetryPacket.deviceId, "REMOVE")
    }

    // добавляем ТС в новую область
    ops.add(
        "cell:$newS2:devices",
        telemetryPacket.deviceId.toString())
    // УДАЛЕНО: publishCellUpdate(newS2, telemetryPacket.deviceId, "ADD")

    val navKey = "nav:${telemetryPacket.deviceId}:last"
    val data = objectMapper.writeValueAsString(telemetryPacket)

    // устанавливаем последние данные по ключу (это триггерит keyspace notification)
    redisTemplate.opsForValue().set(
        navKey,
        data,
        navigationDuration)

    // УДАЛЕНО: publishTelemetryUpdate(telemetryPacket)

    deviceIdToS2Key[telemetryPacket.deviceId] = newS2
}
```

### 6. Обновленный полный код класса
```kotlin
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
```

## Проверка изменений
1. Убедиться, что операция `redisTemplate.opsForValue().set()` продолжает работать
2. Проверить, что keyspace notifications срабатывают при set операциях
3. Убедиться, что `cell:*:devices` sets обновляются корректно
4. Проверить, что `deviceIdToS2Key` и `s2keyToDeviceIdSet` синхронизированы

## Влияние на другие компоненты
1. `TelemetryWebSocketHandler` больше не получает события через pub/sub
2. Все real-time обновления теперь через keyspace notifications
3. Начальная загрузка данных остается без изменений

## Тестирование
1. Unit тесты для проверки логики обновления s2Cell
2. Интеграционные тесты с Redis для проверки keyspace notifications
3. Тесты производительности для оценки нагрузки