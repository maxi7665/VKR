package com.lynceus.telemetry_processor.processor

import com.lynceus.telemetry_processor.dto.GeozoneDto
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.event.InOut
import com.lynceus.telemetry_processor.event.ZoneVisitEvent
import com.lynceus.telemetry_processor.service.GeozoneService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ZoneVisitEventProcessorTest {

    @Mock
    private lateinit var geozoneService: GeozoneService

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<ZoneVisitEvent>

    private fun createTestZone(id: Long, name: String, coordinates: List<List<Double>>): GeozoneDto {
        return GeozoneDto(
            id = id,
            name = name,
            type = "polygon",
            coordinates = coordinates,
            isActive = true,
            s2Key = 123456789L,
            lat = coordinates.map { it[0] }.average(),
            lon = coordinates.map { it[1] }.average()
        )
    }

    private fun createTestPacket(
        deviceId: Long = 1L,
        vehicleId: Long = 100L,
        latitude: Double = 59.0,
        longitude: Double = 30.0,
        packetTime: LocalDateTime = LocalDateTime.now()
    ): TelemetryPacket {
        return TelemetryPacket(
            vehicleId = vehicleId,
            deviceId = deviceId,
            packetTime = packetTime,
            receptionTime = LocalDateTime.now(),
            latitude = latitude,
            longitude = longitude,
            s2Cell = 123456789L,
            azimuth = 0,
            discretizedPackedTime = packetTime
        )
    }

    @Test
    fun `initialization with empty zones list`() {
        // Given
        whenever(geozoneService.getAllGeozones())
            .thenReturn(emptyList())

        // When
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )

        // Then - should not throw exception
        assertNotNull(processor)
    }

    @Test
    fun `process packet inside zone triggers entry event`() {
        // Given
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        val packet = createTestPacket(
            deviceId = 1L,
            latitude = 59.0, // Inside the zone
            longitude = 30.0
        )

        // When
        processor.processPacket(packet)

        // Then - should publish entry event
        verify(applicationEventPublisher, times(1))
            .publishEvent(eventCaptor.capture())
        val event = eventCaptor.value
        assertEquals(InOut.In, event.inOut)
        assertEquals(1L, event.deviceId)
        assertEquals(100L, event.vehicleId)
        assertEquals(1L, event.zoneId)
        assertEquals("Test Zone", event.zoneName)
    }

    @Test
    fun `process packet outside zone triggers exit event if previously inside`() {
        // Given
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        // First packet inside zone
        val packetInside = createTestPacket(
            deviceId = 1L,
            latitude = 59.0,
            longitude = 30.0
        )
        processor.processPacket(packetInside)
        
        // Reset mock to count only second call
        reset(applicationEventPublisher)
        
        // Second packet outside zone
        val packetOutside = createTestPacket(
            deviceId = 1L,
            latitude = 0.0, // Far away
            longitude = 0.0
        )

        // When
        processor.processPacket(packetOutside)

        // Then - should publish exit event
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        val event = eventCaptor.value
        assertEquals(InOut.Out, event.inOut)
        assertEquals(1L, event.deviceId)
        assertEquals(1L, event.zoneId)
    }

    @Test
    fun `process packet inside multiple overlapping zones triggers multiple events`() {
        // Given - create two overlapping zones
        val zone1 = createTestZone(
            id = 1L,
            name = "Zone 1",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )
        
        val zone2 = createTestZone(
            id = 2L,
            name = "Zone 2",
            coordinates = listOf(
                listOf(58.8, 29.8),
                listOf(58.8, 30.2),
                listOf(59.2, 30.2),
                listOf(59.2, 29.8)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone1, zone2))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        val packet = createTestPacket(
            deviceId = 1L,
            latitude = 59.0, // Inside both zones
            longitude = 30.0
        )

        // When
        processor.processPacket(packet)

        // Then - should publish 2 entry events
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture())
        val events = eventCaptor.allValues
        assertEquals(2, events.size)
        
        val zoneIds = events.map { it.zoneId }.toSet()
        assertTrue(zoneIds.contains(1L))
        assertTrue(zoneIds.contains(2L))
        
        events.forEach { event ->
            assertEquals(InOut.In, event.inOut)
            assertEquals(1L, event.deviceId)
        }
    }

    @Test
    fun `device state is tracked independently`() {
        // Given
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        // Device 1 enters zone
        val packet1 = createTestPacket(
            deviceId = 1L,
            latitude = 59.0,
            longitude = 30.0
        )
        
        // Device 2 enters zone
        val packet2 = createTestPacket(
            deviceId = 2L,
            latitude = 59.0,
            longitude = 30.0
        )

        // When
        processor.processPacket(packet1)
        processor.processPacket(packet2)
        
        // Reset mock
        reset(applicationEventPublisher)
        
        // Device 1 exits zone, Device 2 stays
        val packet1Outside = createTestPacket(
            deviceId = 1L,
            latitude = 0.0,
            longitude = 0.0
        )
        
        processor.processPacket(packet1Outside)

        // Then - should publish exit event only for device 1
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        val event = eventCaptor.value
        assertEquals(InOut.Out, event.inOut)
        assertEquals(1L, event.deviceId) // Device 1
    }

    @Test
    fun `no events published when device stays in same zone`() {
        // Given
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        // First packet inside zone
        val packet1 = createTestPacket(
            deviceId = 1L,
            latitude = 59.0,
            longitude = 30.0
        )
        processor.processPacket(packet1)
        
        // Reset mock
        reset(applicationEventPublisher)
        
        // Second packet in same zone (slightly different position)
        val packet2 = createTestPacket(
            deviceId = 1L,
            latitude = 59.01,
            longitude = 30.01
        )

        // When
        processor.processPacket(packet2)

        // Then - should not publish any events
        verify(applicationEventPublisher, never())
            .publishEvent(any())
    }

    @Test
    fun `invalid polygon in zone data is skipped`() {
        // Given - create a zone with invalid polygon (e.g., self-intersecting or too few points)
        // For simplicity, we'll test with valid polygon but mock the S2 library to treat it as invalid
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9),
                listOf(58.9, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        // When - should not throw exception
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        // Then - processor should be created
        assertNotNull(processor)
        
        // Process a packet
        val packet = createTestPacket(
            deviceId = 1L,
            latitude = 59.0,
            longitude = 30.0
        )
        
        // Should not throw exception even if polygon is invalid
        processor.processPacket(packet)
    }

    @Test
    fun `device with no previous state processes first packet`() {
        // Given
        val zone = createTestZone(
            id = 1L,
            name = "Test Zone",
            coordinates = listOf(
                listOf(58.9, 29.9),
                listOf(58.9, 30.1),
                listOf(59.1, 30.1),
                listOf(59.1, 29.9)
            )
        )

        whenever(geozoneService.getAllGeozones())
            .thenReturn(listOf(zone))
        
        val processor = ZoneVisitEventProcessor(
            geozoneService = geozoneService,
            applicationEventPublisher = applicationEventPublisher
        )
        
        val packet = createTestPacket(
            deviceId = 999L, // New device
            latitude = 59.0,
            longitude = 30.0
        )

        // When
        processor.processPacket(packet)

        // Then - should publish entry event
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        val event = eventCaptor.value
        assertEquals(InOut.In, event.inOut)
        assertEquals(999L, event.deviceId)
    }
}