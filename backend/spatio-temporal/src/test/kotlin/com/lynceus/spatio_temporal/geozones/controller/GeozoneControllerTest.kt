package com.lynceus.spatio_temporal.geozones.controller

import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import com.lynceus.spatio_temporal.geozones.GeozoneDto
import com.lynceus.spatio_temporal.geozones.entity.Geozone
import com.lynceus.spatio_temporal.geozones.repository.GeozoneRepository
import com.lynceus.spatio_temporal.geozones.service.RectangleRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeozoneControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var geozoneRepository: GeozoneRepository

    @BeforeEach
    fun setup() {
        // Очищаем базу перед каждым тестом
        geozoneRepository.deleteAll()

        // Создаем тестовые геозоны с разными координатами
        val testGeozones = listOf(
            // Геозона в центре Москвы (должна попасть в прямоугольник)
            Geozone(
                name = "Moscow Center",
                type = "circle",
                coordinates = """[[55.7558, 37.6173]]""",
                isActive = true,
                s2Key = S2CellId.fromLatLng(S2LatLng.fromDegrees(55.7558, 37.6173)).parent(24).id(),
                lat = 55.7558,
                lon = 37.6173
            ),
            // Геозона в Санкт-Петербурге (не должна попасть в прямоугольник)
            Geozone(
                name = "Saint Petersburg",
                type = "circle",
                coordinates = """[[59.9343, 30.3351]]""",
                isActive = true,
                s2Key = S2CellId.fromLatLng(S2LatLng.fromDegrees(59.9343, 30.3351)).parent(24).id(),
                lat = 59.9343,
                lon = 30.3351
            ),
            // Геозона в Москве, но на окраине (должна попасть в прямоугольник)
            Geozone(
                name = "Moscow Outskirts",
                type = "polygon",
                coordinates = """[[55.70, 37.65]]""",
                isActive = true,
                s2Key = S2CellId.fromLatLng(S2LatLng.fromDegrees(55.70, 37.65)).parent(24).id(),
                lat = 55.70,
                lon = 37.65
            )
        )

        geozoneRepository.saveAll(testGeozones)
    }

    @Test
    fun `getGeozonesInRectangle should return only geozones within rectangle`() {
        // Прямоугольник, охватывающий центр Москвы
        val request = RectangleRequest(
            topLeftLat = 55.80,
            topLeftLon = 37.50,
            bottomRightLat = 55.70,
            bottomRightLon = 37.70
        )

        val response = mockMvc.post("/api/geozones/rectangle") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "topLeftLat": ${request.topLeftLat},
                    "topLeftLon": ${request.topLeftLon},
                    "bottomRightLat": ${request.bottomRightLat},
                    "bottomRightLon": ${request.bottomRightLon}
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, response.status)

        val responseContent = response.contentAsString
        val returnedGeozones = parseGeozoneArray(responseContent)

        // Проверяем, что вернулись только геозоны внутри прямоугольника
        assertEquals(2, returnedGeozones.size)

        // Проверяем, что центр каждой возвращенной геозоны внутри прямоугольника
        val minLat = minOf(request.topLeftLat, request.bottomRightLat)
        val maxLat = maxOf(request.topLeftLat, request.bottomRightLat)
        val minLon = minOf(request.topLeftLon, request.bottomRightLon)
        val maxLon = maxOf(request.topLeftLon, request.bottomRightLon)

        returnedGeozones.forEach { geozone ->
            assertTrue(
                geozone.lat >= minLat && geozone.lat <= maxLat,
                "Geozone '${geozone.name}' latitude ${geozone.lat} should be within [$minLat, $maxLat]"
            )
            assertTrue(
                geozone.lon >= minLon && geozone.lon <= maxLon,
                "Geozone '${geozone.name}' longitude ${geozone.lon} should be within [$minLon, $maxLon]"
            )
        }

        // Проверяем, что геозона Санкт-Петербурга не вернулась
        val spbGeozone = returnedGeozones.find { it.name == "Saint Petersburg" }
        assertNull(spbGeozone, "Saint Petersburg geozone should not be in results")
    }

    @Test
    fun `getGeozonesInRectangle should return empty list when no geozones in area`() {
        // Прямоугольник в другой стране (например, в Лондоне)
        val request = RectangleRequest(
            topLeftLat = 51.60,
            topLeftLon = -0.20,
            bottomRightLat = 51.40,
            bottomRightLon = 0.10
        )

        val response = mockMvc.post("/api/geozones/rectangle") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "topLeftLat": ${request.topLeftLat},
                    "topLeftLon": ${request.topLeftLon},
                    "bottomRightLat": ${request.bottomRightLat},
                    "bottomRightLon": ${request.bottomRightLon}
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, response.status)

        val responseContent = response.contentAsString
        val returnedGeozones = parseGeozoneArray(responseContent)

        assertTrue(returnedGeozones.isEmpty(), "Should return empty list for area with no geozones")
    }

    @Test
    fun `getGeozonesInRectangle should return geozones with centers inside rectangle`() {
        // Тестируем другой прямоугольник
        val request = RectangleRequest(
            topLeftLat = 55.80,
            topLeftLon = 37.50,
            bottomRightLat = 55.70,
            bottomRightLon = 37.70
        )

        val response = mockMvc.post("/api/geozones/rectangle") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "topLeftLat": ${request.topLeftLat},
                    "topLeftLon": ${request.topLeftLon},
                    "bottomRightLat": ${request.bottomRightLat},
                    "bottomRightLon": ${request.bottomRightLon}
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, response.status)

        val responseContent = response.contentAsString
        val returnedGeozones = parseGeozoneArray(responseContent)

        // Все возвращенные геозоны должны быть внутри прямоугольника
        val minLat = minOf(request.topLeftLat, request.bottomRightLat)
        val maxLat = maxOf(request.topLeftLat, request.bottomRightLat)
        val minLon = minOf(request.topLeftLon, request.bottomRightLon)
        val maxLon = maxOf(request.topLeftLon, request.bottomRightLon)

        returnedGeozones.forEach { geozone ->
            assertTrue(
                geozone.lat >= minLat && geozone.lat <= maxLat,
                "Geozone '${geozone.name}' latitude should be within rectangle"
            )
            assertTrue(
                geozone.lon >= minLon && geozone.lon <= maxLon,
                "Geozone '${geozone.name}' longitude should be within rectangle"
            )
        }
    }

    private fun parseGeozoneArray(json: String): List<GeozoneDto> {
        val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        return objectMapper.readValue(json, Array<GeozoneDto>::class.java).toList()
    }

    @Test
    fun `createGeozone should create and return geozone`() {
        val newGeozoneDto = GeozoneDto(
            name = "New Test Zone",
            type = "polygon",
            coordinates = listOf(
                listOf(37.6173, 55.7558),
                listOf(37.6231, 55.7512),
                listOf(37.6100, 55.7480)
            ),
            isActive = true,
            s2Key = S2CellId.fromLatLng(S2LatLng.fromDegrees(55.7558, 37.6173)).parent(24).id(),
            lat = 55.7558,
            lon = 37.6173
        )

        mockMvc.post("/api/geozones") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "name": "${newGeozoneDto.name}",
                    "type": "${newGeozoneDto.type}",
                    "coordinates": [
                        [${newGeozoneDto.coordinates[0][0]}, ${newGeozoneDto.coordinates[0][1]}],
                        [${newGeozoneDto.coordinates[1][0]}, ${newGeozoneDto.coordinates[1][1]}],
                        [${newGeozoneDto.coordinates[2][0]}, ${newGeozoneDto.coordinates[2][1]}]
                    ],
                    "isActive": ${newGeozoneDto.isActive},
                    "s2Key": ${newGeozoneDto.s2Key},
                    "lat": ${newGeozoneDto.lat},
                    "lon": ${newGeozoneDto.lon}
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            content {
                contentType(MediaType.APPLICATION_JSON)
            }
        }.andReturn().response

        // Проверяем, что геозона была сохранена в базе данных
        val savedGeozones = geozoneRepository.findAll()
        
        assertTrue(savedGeozones.any { it.name == "New Test Zone" }, "Новая геозона должна быть сохранена в базе")
        
        val createdZone = savedGeozones.find { it.name == "New Test Zone" }!!
        assertEquals("New Test Zone", createdZone.name)
        assertEquals("polygon", createdZone.type)
        assertEquals(true, createdZone.isActive)
        assertEquals(newGeozoneDto.s2Key, createdZone.s2Key)
        assertEquals(newGeozoneDto.lat, createdZone.lat)
        assertEquals(newGeozoneDto.lon, createdZone.lon)
    }
}
