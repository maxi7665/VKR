package com.lynceus.telemetry_processor.repository

import com.lynceus.telemetry_processor.dto.TelemetryIntervalResponse
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.persistence.Tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryPacketRepositoryTest {

    @Mock
    private lateinit var entityManager: EntityManager

    @Mock
    private lateinit var query: Query

    private lateinit var repository: TelemetryPacketRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = TelemetryPacketRepositoryImpl()
        // Use reflection to inject the mocked entityManager
        val field = TelemetryPacketRepositoryImpl::class.java.getDeclaredField("entityManager")
        field.isAccessible = true
        field.set(repository, entityManager)
    }

    @Test
    fun `findByS2KeyInRanges should return empty list when ranges are empty`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val emptyRanges = emptyList<Pair<Long, Long>>()

        // When
        val result = repository.findByS2KeyInRanges(fromDateTime, toDateTime, emptyRanges)

        // Then
        assertTrue(result.isEmpty())
        verify(entityManager, never()).createNativeQuery(any(), any<Class<*>>())
    }

    @Test
    fun `findByS2KeyInRanges should build correct SQL with single range`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val ranges = listOf(Pair(100L, 200L))

        whenever(entityManager.createNativeQuery(any(), eq(Tuple::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList<Any>())

        // When
        repository.findByS2KeyInRanges(fromDateTime, toDateTime, ranges)

        // Then
        // Verify createNativeQuery was called with Tuple class
        verify(entityManager).createNativeQuery(any(), eq(Tuple::class.java))
        verify(query).setParameter("startTime", fromDateTime)
        verify(query).setParameter("endTime", toDateTime)
        verify(query).setParameter("min0", 100L)
        verify(query).setParameter("max0", 200L)
    }

    @Test
    fun `findByS2KeyInRanges should build correct SQL with multiple ranges`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val ranges = listOf(Pair(100L, 200L), Pair(300L, 400L), Pair(500L, 600L))

        whenever(entityManager.createNativeQuery(any(), eq(Tuple::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList<Any>())

        // When
        repository.findByS2KeyInRanges(fromDateTime, toDateTime, ranges)

        // Then
        // Verify createNativeQuery was called with Tuple class
        verify(entityManager).createNativeQuery(any(), eq(Tuple::class.java))
        
        ranges.forEachIndexed { index, (min, max) ->
            verify(query).setParameter("min$index", min)
            verify(query).setParameter("max$index", max)
        }
        verify(query).setParameter("startTime", fromDateTime)
        verify(query).setParameter("endTime", toDateTime)
    }

    @Test
    fun `findByS2KeyInRanges should map results correctly`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val ranges = listOf(Pair(100L, 200L))

        val mockTuple = mock<Tuple>()
        whenever(mockTuple.get("vehicle_id", Number::class.java)).thenReturn(123L)
        whenever(mockTuple.get("device_id", Number::class.java)).thenReturn(456L)
        whenever(mockTuple.get("from_time", Timestamp::class.java)).thenReturn(Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 10, 30, 0)))
        whenever(mockTuple.get("to_time", Timestamp::class.java)).thenReturn(Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 11, 45, 0)))

        whenever(entityManager.createNativeQuery(any(), eq(Tuple::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(listOf(mockTuple))

        // When
        val result = repository.findByS2KeyInRanges(fromDateTime, toDateTime, ranges)

        // Then
        assertEquals(1, result.size)
        val response = result[0]
        assertEquals(123L, response.vehicleId)
        assertEquals(456L, response.deviceId)
        assertEquals(LocalDateTime.of(2024, 1, 1, 10, 30, 0), response.fromDateTime)
        assertEquals(LocalDateTime.of(2024, 1, 1, 11, 45, 0), response.toDateTime)
    }

    @Test
    fun `getDeviceTelemetryInPeriod should execute correct SQL`() {
        // Given
        val deviceId = 789L
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)

        val expectedPacket = TelemetryPacket(
            id = 1L,
            vehicleId = 123L,
            deviceId = deviceId,
            packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            receptionTime = LocalDateTime.of(2024, 1, 1, 12, 1, 0),
            discretizedPackedTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = 100L,
            azimuth = 90
        )

        whenever(entityManager.createNativeQuery(any(), eq(TelemetryPacket::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(listOf(expectedPacket))

        // When
        val result = repository.getDeviceTelemetryInPeriod(deviceId, fromDateTime, toDateTime)

        // Then
        val expectedSql = """
            SELECT * from telemetry_packets p 
            where device_id = :device_id
                and packet_time >= :from_time
                and packet_time <= :to_time
        """.trimIndent()
        
        verify(entityManager).createNativeQuery(eq(expectedSql), eq(TelemetryPacket::class.java))
        verify(query).setParameter("device_id", deviceId)
        verify(query).setParameter("from_time", fromDateTime)
        verify(query).setParameter("to_time", toDateTime)
        
        assertEquals(1, result.size)
        assertEquals(expectedPacket, result[0])
    }

    @Test
    fun `getDeviceTelemetryInPeriod should return empty list when no results`() {
        // Given
        val deviceId = 789L
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)

        whenever(entityManager.createNativeQuery(any(), eq(TelemetryPacket::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList<Any>())

        // When
        val result = repository.getDeviceTelemetryInPeriod(deviceId, fromDateTime, toDateTime)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTelemetryInPeriodWithLimit should execute correct SQL with limit`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val limit = 50L

        val expectedPacket = TelemetryPacket(
            id = 1L,
            vehicleId = 123L,
            deviceId = 456L,
            packetTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            receptionTime = LocalDateTime.of(2024, 1, 1, 12, 1, 0),
            discretizedPackedTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = 100L,
            azimuth = 90
        )

        whenever(entityManager.createNativeQuery(any(), eq(TelemetryPacket::class.java))).thenReturn(query)
        whenever(query.resultList).thenReturn(listOf(expectedPacket))

        // When
        val result = repository.getTelemetryInPeriodWithLimit(fromDateTime, toDateTime, limit)

        // Then
        val expectedSql = """
            SELECT * from telemetry_packets p 
                where packet_time >= :from_time
                and packet_time <= :to_time
            LIMIT :limit
        """.trimIndent()
        
        verify(entityManager).createNativeQuery(eq(expectedSql), eq(TelemetryPacket::class.java))
        verify(query).setParameter("from_time", fromDateTime)
        verify(query).setParameter("to_time", toDateTime)
        verify(query).setParameter("limit", limit)
        
        assertEquals(1, result.size)
        assertEquals(expectedPacket, result[0])
    }

    @Test
    fun `getTelemetryInPeriodWithLimit should respect limit parameter`() {
        // Given
        val fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        val limit = 10L

        val packets = (1..15).map { i ->
            TelemetryPacket(
                id = i.toLong(),
                vehicleId = 100L + i,
                deviceId = 200L + i,
                packetTime = LocalDateTime.of(2024, 1, 1, i, 0, 0),
                receptionTime = LocalDateTime.of(2024, 1, 1, i, 1, 0),
                discretizedPackedTime = LocalDateTime.of(2024, 1, 1, i, 0, 0),
                latitude = 55.7558,
                longitude = 37.6173,
                s2Cell = 100L + i,
                azimuth = 90
            )
        }

        whenever(entityManager.createNativeQuery(any(), eq(TelemetryPacket::class.java))).thenReturn(query)
        // The LIMIT is applied at database level, so our mock should return limited results
        whenever(query.resultList).thenReturn(packets.take(limit.toInt()))

        // When
        val result = repository.getTelemetryInPeriodWithLimit(fromDateTime, toDateTime, limit)

        // Then
        assertEquals(10, result.size)
        verify(query).setParameter("limit", limit)
    }
}