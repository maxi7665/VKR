package com.lynceus.telemetry_processor.service

import com.google.common.geometry.*
import com.lynceus.telemetry_processor.dto.DeviceTelemetryRequest
import com.lynceus.telemetry_processor.dto.PointDto
import com.lynceus.telemetry_processor.dto.PolygonTimeRequest
import com.lynceus.telemetry_processor.dto.TelemetryIntervalResponse
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.processor.NavigationProcessor
import com.lynceus.telemetry_processor.repository.TelemetryPacketRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryQueryServiceTest {

    @Mock
    private lateinit var telemetryPacketRepository: TelemetryPacketRepository

    private lateinit var telemetryQueryService: TelemetryQueryService

    @BeforeEach
    fun setUp() {
        telemetryQueryService = TelemetryQueryService(telemetryPacketRepository)
    }

    @Test
    fun `findTelemetryIntervals should return empty list for empty polygon`() {
        // Given
        val request = PolygonTimeRequest(
            polygon = emptyList(),
            fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        )

        // When
        val result = telemetryQueryService.findTelemetryIntervals(request)

        // Then
        assertTrue(result.isEmpty())
        // Should not call repository for empty polygon
        verify(telemetryPacketRepository, never())
            .findByS2KeyInRanges(any(), any(), any())
    }

    @Test
    fun `findTelemetryIntervals should process basic polygon and call repository`() {
        // Given
        val request = PolygonTimeRequest(
            polygon = listOf(
                PointDto(latitude = 55.7558, longitude = 37.6173),
                PointDto(latitude = 55.7559, longitude = 37.6174),
                PointDto(latitude = 55.7560, longitude = 37.6175)
            ),
            fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        )

        val expectedResponse = listOf(
            TelemetryIntervalResponse(
                vehicleId = 100L,
                deviceId = 200L,
                fromDateTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                toDateTime = LocalDateTime.of(2024, 1, 1, 11, 0, 0)
            )
        )

        whenever(telemetryPacketRepository.findByS2KeyInRanges(any(), any(), any()))
            .thenReturn(expectedResponse)

        // When
        val result = telemetryQueryService.findTelemetryIntervals(request)

        // Then
        assertEquals(expectedResponse, result)
        verify(telemetryPacketRepository).findByS2KeyInRanges(
            eq(request.fromDateTime),
            eq(request.toDateTime),
            any()
        )
    }

    @Test
    fun `getDeviceTelemetryInPeriod should call repository with correct parameters`() {
        // Given
        val request = DeviceTelemetryRequest(
            deviceId = 12345L,
            fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        )

        val expectedPackets = listOf(
            TelemetryPacket(
                vehicleId = 100L,
                deviceId = 12345L,
                packetTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0),
                latitude = 55.7558,
                longitude = 37.6173,
                s2Cell = 1234567890L
            )
        )

        whenever(telemetryPacketRepository.getDeviceTelemetryInPeriod(
            eq(request.deviceId),
            eq(request.fromDateTime),
            eq(request.toDateTime)
        )).thenReturn(expectedPackets)

        // When
        val result = telemetryQueryService.getDeviceTelemetryInPeriod(request)

        // Then
        assertEquals(expectedPackets, result)
        verify(telemetryPacketRepository).getDeviceTelemetryInPeriod(
            request.deviceId,
            request.fromDateTime,
            request.toDateTime
        )
    }

    @Test
    fun `getDeviceTelemetryInPeriod should return empty list when repository returns empty`() {
        // Given
        val request = DeviceTelemetryRequest(
            deviceId = 12345L,
            fromDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            toDateTime = LocalDateTime.of(2024, 1, 2, 0, 0, 0)
        )

        whenever(telemetryPacketRepository.getDeviceTelemetryInPeriod(any(), any(), any()))
            .thenReturn(emptyList())

        // When
        val result = telemetryQueryService.getDeviceTelemetryInPeriod(request)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mergeAdjacentRanges should merge overlapping ranges`() {
        // Given
        val ranges = listOf(
            Pair(100L, 200L),
            Pair(150L, 250L), // Overlaps with first
            Pair(300L, 400L)
        )

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(telemetryQueryService, ranges) as List<Pair<Long, Long>>

        // Then
        assertEquals(2, result.size)
        // First two ranges should be merged
        assertEquals(100L, result[0].first)
        assertEquals(250L, result[0].second)
        // Third range should remain separate
        assertEquals(300L, result[1].first)
        assertEquals(400L, result[1].second)
    }

    @Test
    fun `mergeAdjacentRanges should merge adjacent ranges with difference of 1`() {
        // Given
        val ranges = listOf(
            Pair(100L, 200L),
            Pair(201L, 300L) // Adjacent (200 + 1 = 201)
        )

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(
            telemetryQueryService,
            ranges) as List<Pair<Long, Long>>

        // Then
        assertEquals(1, result.size)
        assertEquals(100L, result[0].first)
        assertEquals(300L, result[0].second)
    }

    @Test
    fun `mergeAdjacentRanges should keep separate ranges when not adjacent`() {
        // Given
        val ranges = listOf(
            Pair(100L, 200L),
            Pair(202L, 300L) // Not adjacent (gap of 1)
        )

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(telemetryQueryService, ranges) as List<Pair<Long, Long>>

        // Then
        assertEquals(2, result.size)
        assertEquals(100L, result[0].first)
        assertEquals(200L, result[0].second)
        assertEquals(202L, result[1].first)
        assertEquals(300L, result[1].second)
    }

    @Test
    fun `mergeAdjacentRanges should return empty list for empty input`() {
        // Given
        val ranges = emptyList<Pair<Long, Long>>()

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(telemetryQueryService, ranges) as List<Pair<Long, Long>>

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mergeAdjacentRanges should return single range unchanged`() {
        // Given
        val ranges = listOf(Pair(100L, 200L))

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(telemetryQueryService, ranges) as List<Pair<Long, Long>>

        // Then
        assertEquals(1, result.size)
        assertEquals(100L, result[0].first)
        assertEquals(200L, result[0].second)
    }

    @Test
    fun `mergeAdjacentRanges should handle multiple merges correctly`() {
        // Given
        val ranges = listOf(
            Pair(100L, 200L),
            Pair(150L, 250L), // Overlaps with first
            Pair(251L, 300L), // Adjacent to second
            Pair(400L, 500L), // Separate
            Pair(450L, 550L)  // Overlaps with fourth
        )

        // Use reflection to test private method
        val method = TelemetryQueryService::class.java.getDeclaredMethod(
            "mergeAdjacentRanges",
            List::class.java
        )
        method.isAccessible = true

        // When
        val result = method.invoke(telemetryQueryService, ranges) as List<Pair<Long, Long>>

        // Then
        assertEquals(2, result.size)
        // First three ranges should be merged into one
        assertEquals(100L, result[0].first)
        assertEquals(300L, result[0].second)
        // Last two ranges should be merged into one
        assertEquals(400L, result[1].first)
        assertEquals(550L, result[1].second)
    }
}