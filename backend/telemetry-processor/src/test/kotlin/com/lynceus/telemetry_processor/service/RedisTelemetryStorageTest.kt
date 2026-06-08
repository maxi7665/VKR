package com.lynceus.telemetry_processor.service

import com.lynceus.telemetry_processor.entity.TelemetryPacket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisTelemetryStorageTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Mock
    private lateinit var valueOps: ValueOperations<String, Any>

    private lateinit var redisTelemetryStorage: RedisTelemetryStorage

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        redisTelemetryStorage = RedisTelemetryStorage(redisTemplate)
    }

    @Test
    fun `save should store packet with correct key`() {
        // Given
        val packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val packet = TelemetryPacket(
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = packetTime,
            s2Cell = 1234567890L
        )
        
        // Calculate expected epoch seconds using same logic as buildKey
        val packetTimeUnix = packetTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val expectedKey = "nav:100:1234567890:200:$packetTimeUnix"

        // When
        redisTelemetryStorage.save(packet)

        // Then
        verify(valueOps).set(eq(expectedKey), eq(packet))
    }

    @Test
    fun `save should handle exception gracefully`() {
        // Given
        val packet = TelemetryPacket(
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = LocalDateTime.now(),
            s2Cell = 1234567890L
        )
        
        whenever(valueOps.set(any(), any())).thenThrow(RuntimeException("Redis connection failed"))

        // When
        redisTelemetryStorage.save(packet)

        // Then
        // Should not throw exception, just log error
        verify(valueOps).set(any(), eq(packet))
    }

    @Test
    fun `saveAll should do nothing for empty list`() {
        // Given
        val emptyList = emptyList<TelemetryPacket>()

        // When
        redisTelemetryStorage.saveAll(emptyList)

        // Then
        verify(redisTemplate, never()).executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())
        verify(valueOps, never()).set(any<String>(), any<Any>())
    }

    @Test
    fun `saveAll should store multiple packets via pipeline`() {
        // Given
        val packets = listOf(
            TelemetryPacket(
                vehicleId = 100L,
                deviceId = 200L,
                packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                s2Cell = 1234567890L
            ),
            TelemetryPacket(
                vehicleId = 101L,
                deviceId = 201L,
                packetTime = LocalDateTime.of(2024, 1, 1, 12, 1, 0),
                s2Cell = 1234567891L
            )
        )

        whenever(redisTemplate.executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())).thenReturn(emptyList<Any>())

        // When
        redisTelemetryStorage.saveAll(packets)

        // Then
        verify(redisTemplate).executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())
    }

    @Test
    fun `saveAll should call set for each packet with correct key and TTL`() {
        // Given
        val packet1 = TelemetryPacket(
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            s2Cell = 1234567890L
        )
        val packet2 = TelemetryPacket(
            vehicleId = 101L,
            deviceId = 201L,
            packetTime = LocalDateTime.of(2024, 1, 1, 12, 1, 0),
            s2Cell = 1234567891L
        )
        val packets = listOf(packet1, packet2)

        val callbackCaptor = argumentCaptor<org.springframework.data.redis.core.RedisCallback<Any>>()

        // Mock the pipeline execution to capture the callback
        whenever(redisTemplate.executePipelined(callbackCaptor.capture())).thenReturn(emptyList<Any>())

        // When
        redisTelemetryStorage.saveAll(packets)

        // Then
        verify(redisTemplate).executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())

        // Execute the captured callback to trigger the set operations
        val callback = callbackCaptor.firstValue
        callback.doInRedis(mock())

        // Verify that set was called for each packet with correct parameters
        verify(valueOps).set(
            argThat { key ->
                key.startsWith("nav:100:1234567890:200:") && key.endsWith(":${packet1.packetTime.atZone(ZoneId.systemDefault()).toEpochSecond()}")
            },
            eq(packet1),
            eq(java.time.Duration.ofDays(1))
        )

        verify(valueOps).set(
            argThat { key ->
                key.startsWith("nav:101:1234567891:201:") && key.endsWith(":${packet2.packetTime.atZone(ZoneId.systemDefault()).toEpochSecond()}")
            },
            eq(packet2),
            eq(java.time.Duration.ofDays(1))
        )
    }

    @Test
    fun `saveAll should handle pipeline exception gracefully`() {
        // Given
        val packets = listOf(
            TelemetryPacket(
                vehicleId = 100L,
                deviceId = 200L,
                packetTime = LocalDateTime.now(),
                s2Cell = 1234567890L
            )
        )

        whenever(redisTemplate.executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())).thenThrow(RuntimeException("Pipeline failed"))

        // When
        redisTelemetryStorage.saveAll(packets)

        // Then
        // Should not throw exception, just log error
        verify(redisTemplate).executePipelined(any<org.springframework.data.redis.core.RedisCallback<Any>>())
    }

    @Test
    fun `get should return packet when found`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"
        val expectedPacket = TelemetryPacket(
            vehicleId = vehicleId,
            deviceId = deviceId,
            packetTime = LocalDateTime.ofEpochSecond(packetTimeUnix, 0, java.time.ZoneOffset.UTC),
            s2Cell = s2Cell
        )

        whenever(valueOps.get(expectedKey)).thenReturn(expectedPacket)

        // When
        val result = redisTelemetryStorage.get(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertEquals(expectedPacket, result)
        verify(valueOps).get(expectedKey)
    }

    @Test
    fun `get should return null when key not found`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(valueOps.get(expectedKey)).thenReturn(null)

        // When
        val result = redisTelemetryStorage.get(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertNull(result)
        verify(valueOps).get(expectedKey)
    }

    @Test
    fun `get should return null when value is wrong type`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(valueOps.get(expectedKey)).thenReturn("not a TelemetryPacket")

        // When
        val result = redisTelemetryStorage.get(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertNull(result)
        verify(valueOps).get(expectedKey)
    }

    @Test
    fun `delete should return true when key deleted successfully`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.delete(expectedKey)).thenReturn(true)

        // When
        val result = redisTelemetryStorage.delete(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertTrue(result)
        verify(redisTemplate).delete(expectedKey)
    }

    @Test
    fun `delete should return false when key not found`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.delete(expectedKey)).thenReturn(false)

        // When
        val result = redisTelemetryStorage.delete(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertFalse(result)
        verify(redisTemplate).delete(expectedKey)
    }

    @Test
    fun `delete should return false when delete returns null`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.delete(expectedKey)).thenReturn(null)

        // When
        val result = redisTelemetryStorage.delete(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertFalse(result)
        verify(redisTemplate).delete(expectedKey)
    }

    @Test
    fun `exists should return true when key exists`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.hasKey(expectedKey)).thenReturn(true)

        // When
        val result = redisTelemetryStorage.exists(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertTrue(result)
        verify(redisTemplate).hasKey(expectedKey)
    }

    @Test
    fun `exists should return false when key does not exist`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.hasKey(expectedKey)).thenReturn(false)

        // When
        val result = redisTelemetryStorage.exists(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertFalse(result)
        verify(redisTemplate).hasKey(expectedKey)
    }

    @Test
    fun `exists should return false when hasKey returns null`() {
        // Given
        val s2Cell = 1234567890L
        val vehicleId = 100L
        val deviceId = 200L
        val packetTimeUnix = 1704110400L
        
        val expectedKey = "1234567890:100:200:1704110400"

        whenever(redisTemplate.hasKey(expectedKey)).thenReturn(null)

        // When
        val result = redisTelemetryStorage.exists(s2Cell, vehicleId, deviceId, packetTimeUnix)

        // Then
        assertFalse(result)
        verify(redisTemplate).hasKey(expectedKey)
    }

    @Test
    fun `buildKey should generate correct key format`() {
        // Given
        val packet = TelemetryPacket(
            vehicleId = 100L,
            deviceId = 200L,
            packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            s2Cell = 1234567890L
        )
        
        // Use reflection to test private method
        val method = RedisTelemetryStorage::class.java.getDeclaredMethod("buildKey", TelemetryPacket::class.java)
        method.isAccessible = true
        
        // When
        val result = method.invoke(redisTelemetryStorage, packet) as String
        
        // Then
        // Expected: "nav:vehicleId:s2Cell:deviceId:packetTimeUnix"
        // packetTimeUnix for 2024-01-01 12:00:00 depends on system timezone
        val expectedPrefix = "nav:100:1234567890:200:"
        assertTrue(result.startsWith(expectedPrefix))
    }
}