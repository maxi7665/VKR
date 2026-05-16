package com.lynceus.telemetry_processor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.geometry.*
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import com.lynceus.telemetry_processor.processor.S2ZoneIndex
import com.lynceus.telemetry_processor.repository.TelemetryPacketRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.measureTime

@ExtendWith(MockitoExtension::class)
@SpringBootTest
class S2RegionTelemetryProcessorTest(
    val applicationContext: org.springframework.context.ApplicationContext
) {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var setOps: SetOperations<String, Any>

    @Mock
    private lateinit var valueOps: ValueOperations<String, Any>

    private lateinit var processor: S2RegionTelemetryProcessor

    private var geozoneService: GeozoneService = applicationContext.getBean(
        GeozoneService::class.java)

    private var telemetryPacketRepository = applicationContext.getBean(
        TelemetryPacketRepository::class.java)

    @BeforeEach
    fun setUp() {
        processor = S2RegionTelemetryProcessor(redisTemplate, objectMapper)
//        `when`(redisTemplate.opsForSet()).thenReturn(setOps)
//        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    fun `should process packet and update Redis without publishing events`() {
        // Given
        val deviceId = 123L
        val oldS2Cell = 1001L
        val newS2Cell = 1002L
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = newS2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        val jsonData = """{"deviceId":$deviceId,"s2Cell":$newS2Cell}"""
        
        // Mock deviceIdToS2Key to return old cell
        val deviceIdToS2Key = getPrivateField<MutableMap<Long, Long>>(processor, "deviceIdToS2Key")
        deviceIdToS2Key[deviceId] = oldS2Cell

        // Mock s2keyToDeviceIdSet
        val s2keyToDeviceIdSet = getPrivateField<MutableMap<Long, MutableSet<Long>>>(processor, "s2keyToDeviceIdSet")
        s2keyToDeviceIdSet[oldS2Cell] = mutableSetOf(deviceId)
        s2keyToDeviceIdSet[newS2Cell] = mutableSetOf()

        `when`(objectMapper.writeValueAsString(telemetryPacket)).thenReturn(jsonData)
        `when`(setOps.remove(eq("cell:$oldS2Cell:devices"), eq(deviceId.toString()))).thenReturn(1L)
        `when`(setOps.add(eq("cell:$newS2Cell:devices"), eq(deviceId.toString()))).thenReturn(1L)

        // When
        processor.processPacket(telemetryPacket)

        // Then
        // Verify Redis operations
        verify(setOps).remove("cell:$oldS2Cell:devices", deviceId.toString())
        verify(setOps).add("cell:$newS2Cell:devices", deviceId.toString())
        verify(valueOps).set(
            eq("nav:$deviceId:last"),
            eq(jsonData),
            eq(Duration.ofDays(1))
        )

        // Verify NO event publishing (convertAndSend should NOT be called)
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString())

        // Verify internal state updated
        assert(deviceIdToS2Key[deviceId] == newS2Cell)
        assert(s2keyToDeviceIdSet[oldS2Cell]?.isEmpty() == true)
        assert(s2keyToDeviceIdSet[newS2Cell]?.contains(deviceId) == true)
    }

    @Test
    fun `should process packet for new device without old cell`() {
        // Given
        val deviceId = 123L
        val newS2Cell = 1002L
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = newS2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        val jsonData = """{"deviceId":$deviceId,"s2Cell":$newS2Cell}"""
        
        // Device not in deviceIdToS2Key
        val deviceIdToS2Key = getPrivateField<MutableMap<Long, Long>>(processor, "deviceIdToS2Key")
        deviceIdToS2Key.clear()

        `when`(objectMapper.writeValueAsString(telemetryPacket)).thenReturn(jsonData)
        `when`(setOps.add(eq("cell:$newS2Cell:devices"), eq(deviceId.toString()))).thenReturn(1L)

        // When
        processor.processPacket(telemetryPacket)

        // Then
        // Should NOT call remove for old cell
        verify(setOps, never()).remove(anyString(), anyString())
        verify(setOps).add("cell:$newS2Cell:devices", deviceId.toString())
        verify(valueOps).set(
            eq("nav:$deviceId:last"),
            eq(jsonData),
            eq(Duration.ofDays(1))
        )

        // Verify internal state updated
        assert(deviceIdToS2Key[deviceId] == newS2Cell)
    }

    @Test
    fun `should process packet with same cell`() {
        // Given
        val deviceId = 123L
        val s2Cell = 1001L
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = s2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        val jsonData = """{"deviceId":$deviceId,"s2Cell":$s2Cell}"""
        
        // Device already in the same cell
        val deviceIdToS2Key = getPrivateField<MutableMap<Long, Long>>(processor, "deviceIdToS2Key")
        deviceIdToS2Key[deviceId] = s2Cell

        val s2keyToDeviceIdSet = getPrivateField<MutableMap<Long, MutableSet<Long>>>(processor, "s2keyToDeviceIdSet")
        s2keyToDeviceIdSet[s2Cell] = mutableSetOf(deviceId)

        `when`(objectMapper.writeValueAsString(telemetryPacket)).thenReturn(jsonData)
        `when`(setOps.add(eq("cell:$s2Cell:devices"), eq(deviceId.toString()))).thenReturn(0L) // Already in set

        // When
        processor.processPacket(telemetryPacket)

        // Then
        // Should still call add (Redis set handles duplicates)
        verify(setOps).add("cell:$s2Cell:devices", deviceId.toString())
        // Should NOT call remove
        verify(setOps, never()).remove(anyString(), anyString())
        verify(valueOps).set(
            eq("nav:$deviceId:last"),
            eq(jsonData),
            eq(Duration.ofDays(1))
        )
    }

    // Helper method to access private fields for testing
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }

    @Test
    fun getCellChildren(){
        val cell = 5086308267525668864;
        val cellId = S2CellId(cell)


        println(cellId.level())
        println(cellId.childBegin(15).id())
        println(cellId.childEnd(15).id())
    }

    /**
     * Сравнение встроенного S2-индекса и самописного
     */
    @Test
    fun indexesComparison() {
        val zones = geozoneService.getAllGeozones()

        val regions = zones.map {
            val s2Loop = S2Loop(
                it.coordinates.map { S2LatLng.fromDegrees(
                    it[0],
                    it[1]).toPoint() }
            )
            s2Loop.normalize()
            S2Polygon(s2Loop)
        }

        val indexMaxLevel = 24;

        val zoneCoverer = S2RegionCoverer.builder()
            .setMaxCells(200)
            .setMinLevel(1)
            .setMaxLevel(indexMaxLevel)
            .build()


        val zoneIndex = S2ZoneIndex(
            regions,
            zoneCoverer,
            indexMaxLevel)

        val shapeIndex = S2ShapeIndex()

        val shapeToRegion = regions.groupBy { it.shape() }

        shapeToRegion.forEach { shapeIndex.add(it.key) }

        val containsPointQuery = S2ContainsPointQuery(shapeIndex)

//        val telemetry = telemetryPacketRepository.getTelemetryInPeriodWithLimit(
//            LocalDateTime.of(2026,4,1,0,0),
//            LocalDateTime.of(2026,6,1,0,0),
//            50000
//        ).toMutableList()
//
//        assert(telemetry.isNotEmpty())
//
//        // наращиваем телеметрию до нужных значений
//        while (telemetry.size <= 100000) {
//            telemetry += telemetry
//        }
//
//        val points = telemetry.map {
//            val point = S2LatLng.fromDegrees(
//                it.latitude,
//                it.longitude).toPoint()
//
//            Pair(point, S2Cell(point).id().parent(indexMaxLevel))
//         }

        val points = zones.map{ S2LatLng.fromDegrees(
            it.lat,
            it.lon).toPoint() }

//        for (point in points) {
//
//
//
//            val shapeZones = containsPointQuery.getContainingShapeIds(
//                point)
//            val zoneZones = zoneIndex.findRegions(
//                point
//                )
//
////            assert(zoneZones.size() > 0)
//
//            if (!shapeZones.isEqualTo(zoneZones)){
//                println("here")
//            }
//
//            assert(shapeZones.isEqualTo(zoneZones))
//
//
//        }

        val queryTime = measureTime {
            for (point in points) {
                val shapeZones = containsPointQuery
                    .getContainingShapeIds(point)
            }
        }

        val indexTime = measureTime {
            for (point in points) {
                val zoneZones = zoneIndex.findRegions(
                    point
                )
            }
        }

        println(queryTime)
        println(indexTime)
    }
}