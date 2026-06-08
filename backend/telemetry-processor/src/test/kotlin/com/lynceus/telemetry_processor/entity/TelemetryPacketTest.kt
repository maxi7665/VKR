package com.lynceus.telemetry_processor.entity

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TelemetryPacketTest {

    @Test
    fun `create TelemetryPacket with default values`() {
        val packet = TelemetryPacket()
        assertEquals(null, packet.id)
        assertEquals(0L, packet.vehicleId)
        assertEquals(0L, packet.deviceId)
        assertEquals(LocalDateTime.MIN, packet.packetTime)
        assertEquals(LocalDateTime.MIN, packet.receptionTime)
        assertEquals(LocalDateTime.MIN, packet.discretizedPackedTime)
        assertEquals(0.0, packet.latitude)
        assertEquals(0.0, packet.longitude)
        assertEquals(0L, packet.s2Cell)
        assertEquals(0.toShort(), packet.azimuth)
    }

    @Test
    fun `create TelemetryPacket with custom values`() {
        val now = LocalDateTime.now()
        val packet = TelemetryPacket(
            id = 1L,
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = now,
            receptionTime = now.plusMinutes(1),
            discretizedPackedTime = now.plusMinutes(2),
            latitude = 59.74256,
            longitude = 30.31551,
            s2Cell = 1234567890L,
            azimuth = 90.toShort()
        )

        assertEquals(1L, packet.id)
        assertEquals(100L, packet.vehicleId)
        assertEquals(200L, packet.deviceId)
        assertEquals(now, packet.packetTime)
        assertEquals(now.plusMinutes(1), packet.receptionTime)
        assertEquals(now.plusMinutes(2), packet.discretizedPackedTime)
        assertEquals(59.74256, packet.latitude)
        assertEquals(30.31551, packet.longitude)
        assertEquals(1234567890L, packet.s2Cell)
        assertEquals(90.toShort(), packet.azimuth)
    }

    @Test
    fun `equals returns true for same id`() {
        val packet1 = TelemetryPacket(id = 5L, vehicleId = 10L)
        val packet2 = TelemetryPacket(id = 5L, vehicleId = 20L) // different vehicleId but same id

        assertTrue(packet1 == packet2, "Packets with same id should be equal")
        assertTrue(packet1.equals(packet2))
    }

    @Test
    fun `equals returns false for different id`() {
        val packet1 = TelemetryPacket(id = 5L)
        val packet2 = TelemetryPacket(id = 6L)

        assertFalse(packet1 == packet2, "Packets with different id should not be equal")
        assertFalse(packet1.equals(packet2))
    }

    @Test
    fun `equals returns false when id is null`() {
        val packet1 = TelemetryPacket(id = null)
        val packet2 = TelemetryPacket(id = null)

        // According to the equals implementation, if id is null, equality is based on reference?
        // Actually the equals method returns true only if id != null && id == other.id
        // If id is null, it returns false (since id != null condition fails).
        // Let's test that.
        assertFalse(packet1 == packet2, "Packets with null id should not be equal (by implementation)")
    }

    @Test
    fun `equals returns false when comparing with null`() {
        val packet = TelemetryPacket(id = 1L)
        assertFalse(packet.equals(null))
    }

    @Test
    fun `equals returns false when comparing with different class`() {
        val packet = TelemetryPacket(id = 1L)
        val other = Any()
        assertFalse(packet.equals(other))
    }

    @Test
    fun `equals returns true for same instance`() {
        val packet = TelemetryPacket(id = 1L)
        assertTrue(packet.equals(packet))
        assertTrue(packet == packet)
    }

    @Test
    fun `equals returns false when id is null vs non-null`() {
        val packet1 = TelemetryPacket(id = null)
        val packet2 = TelemetryPacket(id = 1L)
        assertFalse(packet1 == packet2)
        assertFalse(packet2 == packet1)
    }

    @Test
    fun `equals returns false when id is null but other fields same`() {
        val packet1 = TelemetryPacket(id = null, vehicleId = 100L, deviceId = 200L)
        val packet2 = TelemetryPacket(id = null, vehicleId = 100L, deviceId = 200L)
        // id null -> equals false
        assertFalse(packet1 == packet2)
    }

    @Test
    fun `equals returns true when id same but other fields different`() {
        val packet1 = TelemetryPacket(id = 5L, vehicleId = 10L, latitude = 1.0)
        val packet2 = TelemetryPacket(id = 5L, vehicleId = 20L, latitude = 2.0)
        assertTrue(packet1 == packet2)
    }

    @Test
    fun `hashCode consistent with equals`() {
        val packet1 = TelemetryPacket(id = 7L)
        val packet2 = TelemetryPacket(id = 7L)

        assertEquals(packet1.hashCode(), packet2.hashCode())
    }

    @Test
    fun `hashCode same for different instances of same class`() {
        val packet1 = TelemetryPacket(id = 1L)
        val packet2 = TelemetryPacket(id = 2L)
        val packet3 = TelemetryPacket(id = null)
        // All non-proxy instances have same hashCode (javaClass.hashCode())
        assertEquals(packet1.hashCode(), packet2.hashCode())
        assertEquals(packet1.hashCode(), packet3.hashCode())
    }

    @Test
    fun `hashCode does not depend on id`() {
        val packetWithId = TelemetryPacket(id = 99L)
        val packetWithoutId = TelemetryPacket(id = null)
        assertEquals(packetWithId.hashCode(), packetWithoutId.hashCode())
    }

    @Test
    fun `hashCode does not depend on other fields`() {
        val packet1 = TelemetryPacket(id = 1L, vehicleId = 10L, latitude = 1.0)
        val packet2 = TelemetryPacket(id = 1L, vehicleId = 20L, latitude = 2.0)
        assertEquals(packet1.hashCode(), packet2.hashCode())
    }

    @Test
    fun `toString contains class name and fields`() {
        val packet = TelemetryPacket(
            id = 42L,
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = LocalDateTime.of(2023, 1, 1, 12, 0),
            receptionTime = LocalDateTime.of(2023, 1, 1, 12, 1),
            latitude = 59.7,
            longitude = 30.3,
            s2Cell = 999L,
            azimuth = 45.toShort()
        )

        val str = packet.toString()
        assertTrue(str.startsWith("TelemetryPacket("))
        assertTrue("id = 42" in str)
        assertTrue("vehicleId = 100" in str)
        assertTrue("deviceId = 200" in str)
        assertTrue("latitude = 59.7" in str)
        assertTrue("longitude = 30.3" in str)
        assertTrue("s2Cell = 999" in str)
        assertTrue("azimuth = 45" in str)
    }
}