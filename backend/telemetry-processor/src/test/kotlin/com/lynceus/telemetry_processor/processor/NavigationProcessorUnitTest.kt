package com.lynceus.telemetry_processor.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.repository.TelemetryPacketRepository
import com.lynceus.telemetry_processor.service.S2RegionTelemetryProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import kotlin.text.Charsets
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class NavigationProcessorUnitTest {

    @Mock
    private lateinit var telemetryPacketRepository: TelemetryPacketRepository

    private lateinit var s2RegionTelemetryProcessor: S2RegionTelemetryProcessor
    private lateinit var zoneVisitEventProcessor: ZoneVisitEventProcessor
    private lateinit var navigationProcessor: NavigationProcessor

    @Captor
    private lateinit var packetCaptor: ArgumentCaptor<Iterable<TelemetryPacket>>

    private val sampleJson = """
        {
            "isTachoValid": false,
            "isCanValid": false,
            "id_obj": 81006625,
            "date_real": 1777576017000,
            "latitude": 59.7425616625097,
            "longitude": 30.315516649353206,
            "altitude": 60,
            "speed": 50,
            "course": 304,
            "valid": 1,
            "odometer": 67700,
            "input": 1,
            "event": 1,
            "vdop": 0,
            "hdop": 1,
            "pdop": 1,
            "sat": 20,
            "temperature": [
                {
                    "lversion": 7,
                    "version": "1.0.0.0",
                    "typeSensor": 1,
                    "typeSensor_desc": "Датчик темпиратуры",
                    "serialNumber": 1,
                    "serialNumber_desc": "Порядковый номер датчика",
                    "lSensorAddr": 16,
                    "sensorAddr": "281e5f48b121069f",
                    "sensorAddr_desc": "Адрес датчика, данные с которого поступили",
                    "sensorData": 18.0,
                    "sensorStatesFlag": true,
                    "sensorStatesFlag_desc": "включен/выключен",
                    "lAddData": 1,
                    "addData": "1",
                    "addData_desc": "внутренний датчик температуры"
                },
                {
                    "lversion": 7,
                    "version": "1.0.0.0",
                    "typeSensor": 1,
                    "typeSensor_desc": "Датчик темпиратуры",
                    "serialNumber": 2,
                    "serialNumber_desc": "Порядковый номер датчика",
                    "lSensorAddr": 16,
                    "sensorAddr": "287aef92b2210631",
                    "sensorAddr_desc": "Адрес датчика, данные с которого поступили",
                    "sensorData": 6.0,
                    "sensorStatesFlag": true,
                    "sensorStatesFlag_desc": "включен/выключен",
                    "lAddData": 1,
                    "addData": "2",
                    "addData_desc": "внешний датчик температуры"
                }
            ],
            "typeRS_id": 1,
            "date_turn": 1777576018014,
            "connectRsTs_id": 43668,
            "num_garage": "6625",
            "geo_hesh": "udtecgg1dsxt",
            "line": 0,
            "error": 0,
            "way": 56.618130717189686
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        // Создать моки вручную
        s2RegionTelemetryProcessor = mock<S2RegionTelemetryProcessor>()
        zoneVisitEventProcessor = mock<ZoneVisitEventProcessor>()
        
        navigationProcessor = NavigationProcessor(
            telemetryPacketRepository = telemetryPacketRepository,
            objectMapper = ObjectMapper(),
            redisTelemetryStorage = s2RegionTelemetryProcessor,
            zoneVisitEventProcessor = zoneVisitEventProcessor
        )
    }

    @Test
    fun `process valid JSON and save to repository`() {
        // Given
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        // Setup capture before the method is called
        val capturedPackets = mutableListOf<TelemetryPacket>()
        doAnswer { invocation ->
            val packets = invocation.getArgument<Iterable<TelemetryPacket>>(0)
            capturedPackets.addAll(packets.toList())
            null
        }.`when`(telemetryPacketRepository).saveAll(
            any<Iterable<TelemetryPacket>>())

        // When
        navigationProcessor.process(jsonBytes)

        // Then - verify that buffer added packet
        // (but not flushed because batch size < 100)
        // We need to manually flush to trigger saveAll
        navigationProcessor.flushRemaining()

        // Verify saveAll was called
        verify(telemetryPacketRepository)
            .saveAll(any<Iterable<TelemetryPacket>>())

        // Assert
        assertEquals(1, capturedPackets.size, "Expected 1 packet to be saved")
        val packet = capturedPackets[0]
        assertEquals(6625L, packet.vehicleId)
        assertEquals(81006625L, packet.deviceId)
        assertEquals(59.7425616625097, packet.latitude)
        assertEquals(30.315516649353206, packet.longitude)
        assertTrue(packet.s2Cell != 0L, "S2 cell should be calculated (non-zero)")

        // Check timestamps (convert epoch to LocalDateTime)
        val expectedPacketTime = Instant.ofEpochMilli(1777576017000)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val expectedReceptionTime = Instant.ofEpochMilli(1777576018014)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        assertEquals(expectedPacketTime, packet.packetTime)
        assertEquals(expectedReceptionTime, packet.receptionTime)
    }

    @Test
    fun `process invalid JSON missing required field`() {
        val invalidJson = """{"id_obj": 123}"""
        val jsonBytes = invalidJson.toByteArray(Charsets.UTF_8)

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        // Should not call saveAll because validation fails
        verify(telemetryPacketRepository, never())
            .saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `batch saving when buffer reaches 100`() {
        // Given
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)

        // When - process 100 messages
        repeat(100) {
            navigationProcessor.process(jsonBytes)
        }

        // Then - saveAll should have been called exactly once (auto-flush)
        verify(telemetryPacketRepository, times(1))
            .saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `process JSON with vehicleId as number not string`() {
        val jsonWithNumericVehicleId = """
            {
                "num_garage": 6625,
                "id_obj": 81006625,
                "date_real": 1777576017000,
                "date_turn": 1777576018014,
                "latitude": 59.7425616625097,
                "longitude": 30.315516649353206,
                "course": 304
            }
        """.trimIndent()
        
        val jsonBytes = jsonWithNumericVehicleId.toByteArray(Charsets.UTF_8)
        val capturedPackets = mutableListOf<TelemetryPacket>()
        doAnswer { invocation ->
            val packets = invocation.getArgument<Iterable<TelemetryPacket>>(0)
            capturedPackets.addAll(packets.toList())
            null
        }.`when`(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        verify(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())
        assertEquals(1, capturedPackets.size)
        assertEquals(6625L, capturedPackets[0].vehicleId)
    }

    @Test
    fun `process JSON with invalid vehicleId string should fail`() {
        val jsonWithInvalidVehicleId = """
            {
                "num_garage": "not-a-number",
                "id_obj": 81006625,
                "date_real": 1777576017000,
                "date_turn": 1777576018014,
                "latitude": 59.7425616625097,
                "longitude": 30.315516649353206,
                "course": 304
            }
        """.trimIndent()
        
        val jsonBytes = jsonWithInvalidVehicleId.toByteArray(Charsets.UTF_8)

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        // Should not call saveAll because validation fails
        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `process JSON missing deviceId should fail`() {
        val jsonMissingDeviceId = """
            {
                "num_garage": "6625",
                "date_real": 1777576017000,
                "date_turn": 1777576018014,
                "latitude": 59.7425616625097,
                "longitude": 30.315516649353206,
                "course": 304
            }
        """.trimIndent()
        
        val jsonBytes = jsonMissingDeviceId.toByteArray(Charsets.UTF_8)

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `process JSON missing latitude should fail`() {
        val jsonMissingLatitude = """
            {
                "num_garage": "6625",
                "id_obj": 81006625,
                "date_real": 1777576017000,
                "date_turn": 1777576018014,
                "longitude": 30.315516649353206,
                "course": 304
            }
        """.trimIndent()
        
        val jsonBytes = jsonMissingLatitude.toByteArray(Charsets.UTF_8)

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `process JSON with malformed JSON should handle exception`() {
        val malformedJson = """{invalid json"""
        val jsonBytes = malformedJson.toByteArray(Charsets.UTF_8)

        // Should not throw exception
        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `flushRemaining when buffer is empty should not call repository`() {
        navigationProcessor.flushRemaining()
        
        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `buffer should not flush when size less than batch size`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        // Process 50 messages (less than BATCH_SIZE = 100)
        repeat(50) {
            navigationProcessor.process(jsonBytes)
        }
        
        // Should not auto-flush
        verify(telemetryPacketRepository, never()).saveAll(any<Iterable<TelemetryPacket>>())
        
        // Manual flush should work
        navigationProcessor.flushRemaining()
        verify(telemetryPacketRepository, times(1)).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `verify redisTelemetryStorage is called for each packet`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()
        
        // Verify redisTelemetryStorage.processPacket was called
        verify(s2RegionTelemetryProcessor, times(1)).processPacket(any<TelemetryPacket>())
    }

    @Test
    fun `verify zoneVisitEventProcessor is called for each packet`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()
        
        // Verify zoneVisitEventProcessor.processPacket was called
        verify(zoneVisitEventProcessor, times(1)).processPacket(any<TelemetryPacket>())
    }

    @Test
    fun `exception during repository save should be handled`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        // Make repository.saveAll throw exception
        whenever(telemetryPacketRepository.saveAll(any<Iterable<TelemetryPacket>>()))
            .thenThrow(RuntimeException("Database error"))
        
        // Should not throw exception
        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()
        
        // Verify saveAll was called (even though it threw)
        verify(telemetryPacketRepository, times(1)).saveAll(any<Iterable<TelemetryPacket>>())
    }

    @Test
    fun `verify S2 cell is calculated and set`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        val capturedPackets = mutableListOf<TelemetryPacket>()
        doAnswer { invocation ->
            val packets = invocation.getArgument<Iterable<TelemetryPacket>>(0)
            capturedPackets.addAll(packets.toList())
            null
        }.`when`(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        assertEquals(1, capturedPackets.size)
        val packet = capturedPackets[0]
        
        // S2 cell should be set (non-zero)
        assertNotNull(packet.s2Cell)
        assertTrue(packet.s2Cell != 0L, "S2 cell should be calculated")
    }

    @Test
    fun `verify discretized time is calculated correctly`() {
        val jsonWithSpecificTime = """
            {
                "num_garage": "6625",
                "id_obj": 81006625,
                "date_real": 1777576017000,
                "date_turn": 1777576018014,
                "latitude": 59.7425616625097,
                "longitude": 30.315516649353206,
                "course": 304
            }
        """.trimIndent()
        
        val jsonBytes = jsonWithSpecificTime.toByteArray(Charsets.UTF_8)
        
        val capturedPackets = mutableListOf<TelemetryPacket>()
        doAnswer { invocation ->
            val packets = invocation.getArgument<Iterable<TelemetryPacket>>(0)
            capturedPackets.addAll(packets.toList())
            null
        }.`when`(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())

        navigationProcessor.process(jsonBytes)
        navigationProcessor.flushRemaining()

        assertEquals(1, capturedPackets.size)
        val packet = capturedPackets[0]
        
        // discretizedPackedTime should be set
        assertNotNull(packet.discretizedPackedTime)
        
        // Should be truncated to 5-minute intervals
        val packetTime = packet.packetTime
        val discretizedTime = packet.discretizedPackedTime
        
        // The minute should be a multiple of 5
        val minute = discretizedTime.minute
        assertTrue(minute % 5 == 0, "Discretized time minute should be multiple of 5, but was $minute")
    }

    @Test
    fun `multiple flushes should work correctly`() {
        val jsonBytes = sampleJson.toByteArray(Charsets.UTF_8)
        
        // Process 150 messages (1.5 batches)
        repeat(150) {
            navigationProcessor.process(jsonBytes)
        }
        
        // Should have auto-flushed once at 100, and we'll manually flush the remaining 50
        navigationProcessor.flushRemaining()
        
        // Total: 2 calls (auto-flush at 100 + manual flush of remaining 50)
        verify(telemetryPacketRepository, times(2)).saveAll(any<Iterable<TelemetryPacket>>())
    }
}