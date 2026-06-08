package com.lynceus.telemetry_processor.event

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RedisEventTest {

    @Test
    fun `CellUpdateEvent should have correct properties`() {
        val timestamp = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val event = CellUpdateEvent(
            cellId = 12345L,
            deviceId = 67890L,
            action = "ADD",
            timestamp = timestamp
        )

        assertEquals(12345L, event.cellId)
        assertEquals(67890L, event.deviceId)
        assertEquals("ADD", event.action)
        assertEquals(timestamp, event.timestamp)
    }

    @Test
    fun `CellUpdateEvent should have default timestamp`() {
        val before = LocalDateTime.now()
        val event = CellUpdateEvent(
            cellId = 1L,
            deviceId = 2L,
            action = "REMOVE"
        )
        val after = LocalDateTime.now()

        assertTrue(event.timestamp.isAfter(before) || event.timestamp.isEqual(before))
        assertTrue(event.timestamp.isBefore(after) || event.timestamp.isEqual(after))
    }

    @Test
    fun `CellUpdateEvent equals and hashCode`() {
        val timestamp = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val event1 = CellUpdateEvent(1L, 2L, "ADD", timestamp)
        val event2 = CellUpdateEvent(1L, 2L, "ADD", timestamp)
        val event3 = CellUpdateEvent(1L, 2L, "REMOVE", timestamp)
        val event4 = CellUpdateEvent(2L, 2L, "ADD", timestamp)

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
        assertNotEquals(event1, event3)
        assertNotEquals(event1, event4)
    }

    @Test
    fun `CellUpdateEvent copy`() {
        val original = CellUpdateEvent(1L, 2L, "ADD", LocalDateTime.of(2024, 1, 1, 12, 0, 0))
        val copied = original.copy(action = "REMOVE")

        assertEquals(1L, copied.cellId)
        assertEquals(2L, copied.deviceId)
        assertEquals("REMOVE", copied.action)
        assertEquals(original.timestamp, copied.timestamp)
    }

    @Test
    fun `TelemetryUpdateEvent should have correct properties`() {
        val packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val timestamp = LocalDateTime.of(2024, 1, 1, 12, 0, 1)
        val event = TelemetryUpdateEvent(
            deviceId = 100L,
            vehicleId = 200L,
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort(),
            packetTime = packetTime,
            timestamp = timestamp
        )

        assertEquals(100L, event.deviceId)
        assertEquals(200L, event.vehicleId)
        assertEquals(59.74256, event.latitude)
        assertEquals(30.31551, event.longitude)
        assertEquals(1234567890L, event.s2Cell)
        assertEquals(90.toShort(), event.azimuth)
        assertEquals(packetTime, event.packetTime)
        assertEquals(timestamp, event.timestamp)
    }

    @Test
    fun `TelemetryUpdateEvent should have default timestamp`() {
        val packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val before = LocalDateTime.now()
        val event = TelemetryUpdateEvent(
            deviceId = 100L,
            vehicleId = 200L,
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort(),
            packetTime = packetTime
        )
        val after = LocalDateTime.now()

        assertTrue(event.timestamp.isAfter(before) || event.timestamp.isEqual(before))
        assertTrue(event.timestamp.isBefore(after) || event.timestamp.isEqual(after))
    }

    @Test
    fun `TelemetryUpdateEvent equals and hashCode`() {
        val packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val timestamp = LocalDateTime.of(2024, 1, 1, 12, 0, 1)
        val event1 = TelemetryUpdateEvent(
            deviceId = 100L,
            vehicleId = 200L,
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort(),
            packetTime = packetTime,
            timestamp = timestamp
        )
        val event2 = TelemetryUpdateEvent(
            deviceId = 100L,
            vehicleId = 200L,
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort(),
            packetTime = packetTime,
            timestamp = timestamp
        )
        val event3 = TelemetryUpdateEvent(
            deviceId = 101L,
            vehicleId = 200L,
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort(),
            packetTime = packetTime,
            timestamp = timestamp
        )

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
        assertNotEquals(event1, event3)
    }
}