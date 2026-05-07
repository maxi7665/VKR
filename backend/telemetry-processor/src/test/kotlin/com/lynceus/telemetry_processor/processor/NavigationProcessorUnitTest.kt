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
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class NavigationProcessorUnitTest {

    @Mock
    private lateinit var telemetryPacketRepository: TelemetryPacketRepository

    private lateinit var navigationProcessor: NavigationProcessor

    private lateinit var s2RegionTelemetryProcessor: S2RegionTelemetryProcessor

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
        navigationProcessor = NavigationProcessor(
            telemetryPacketRepository = telemetryPacketRepository,
            objectMapper = ObjectMapper(),
            redisTelemetryStorage = s2RegionTelemetryProcessor
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
        }.`when`(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())

        // When
        navigationProcessor.process(jsonBytes)

        // Then - verify that buffer added packet (but not flushed because batch size < 100)
        // We need to manually flush to trigger saveAll
        navigationProcessor.flushRemaining()

        // Verify saveAll was called
        verify(telemetryPacketRepository).saveAll(any<Iterable<TelemetryPacket>>())

        // Assert
        assertEquals(1, capturedPackets.size, "Expected 1 packet to be saved")
        val packet = capturedPackets[0]
        assertEquals(6625L, packet.vehicleId)
        assertEquals(81006625L, packet.deviceId)
        assertEquals(59.7425616625097, packet.latitude)
        assertEquals(30.315516649353206, packet.longitude)
        assertEquals(0L, packet.s2Cell)

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
        verify(telemetryPacketRepository, never()).saveAll(anyList())
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
        verify(telemetryPacketRepository, times(1)).saveAll(anyList())
    }
}