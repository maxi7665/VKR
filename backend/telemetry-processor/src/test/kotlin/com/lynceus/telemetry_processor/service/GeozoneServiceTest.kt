package com.lynceus.telemetry_processor.service

import com.lynceus.telemetry_processor.config.GeozoneProperties
import com.lynceus.telemetry_processor.dto.GeozoneDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class GeozoneServiceTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var geozoneProperties: GeozoneProperties

    private lateinit var geozoneService: GeozoneService

    @BeforeEach
    fun setUp() {
        val serviceProperties = GeozoneProperties.ServiceProperties("http://test-geozone-service/api/geozones")
        geozoneProperties = GeozoneProperties(serviceProperties)
        geozoneService = GeozoneService(restTemplate, geozoneProperties)
    }

    @Test
    fun `getAllGeozones should return list when HTTP call succeeds`() {
        // Given
        val expectedGeozones = arrayOf(
            GeozoneDto(
                id = 1L,
                name = "Test Zone 1",
                type = "polygon",
                coordinates = listOf(listOf(1.0, 2.0)),
                isActive = true,
                s2Key = 1001L,
                lat = 55.7558,
                lon = 37.6173
            ),
            GeozoneDto(
                id = 2L,
                name = "Test Zone 2",
                type = "circle",
                coordinates = listOf(listOf(3.0, 4.0)),
                isActive = false,
                s2Key = 1002L,
                lat = 55.7559,
                lon = 37.6174
            )
        )

        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(expectedGeozones)

        // When
        val result = geozoneService.getAllGeozones()

        // Then
        assertEquals(2, result.size)
        assertEquals(expectedGeozones[0], result[0])
        assertEquals(expectedGeozones[1], result[1])
        verify(restTemplate).getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java)
    }

    @Test
    fun `getAllGeozones should return empty list when HTTP returns null`() {
        // Given
        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(null)

        // When
        val result = geozoneService.getAllGeozones()

        // Then
        assertTrue(result.isEmpty())
        verify(restTemplate).getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java)
    }

    @Test
    fun `getAllGeozones should return empty list when HTTP call throws exception`() {
        // Given
        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenThrow(RestClientException("Connection failed"))

        // When
        val result = geozoneService.getAllGeozones()

        // Then
        assertTrue(result.isEmpty())
        verify(restTemplate).getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java)
    }

    @Test
    fun `getActiveGeozones should filter only active geozones`() {
        // Given
        val geozones = arrayOf(
            GeozoneDto(
                id = 1L,
                name = "Active Zone",
                type = "polygon",
                coordinates = listOf(listOf(1.0, 2.0)),
                isActive = true,
                s2Key = 1001L,
                lat = 55.7558,
                lon = 37.6173
            ),
            GeozoneDto(
                id = 2L,
                name = "Inactive Zone",
                type = "circle",
                coordinates = listOf(listOf(3.0, 4.0)),
                isActive = false,
                s2Key = 1002L,
                lat = 55.7559,
                lon = 37.6174
            ),
            GeozoneDto(
                id = 3L,
                name = "Another Active Zone",
                type = "polygon",
                coordinates = listOf(listOf(5.0, 6.0)),
                isActive = true,
                s2Key = 1003L,
                lat = 55.7560,
                lon = 37.6175
            )
        )

        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(geozones)

        // When
        val result = geozoneService.getActiveGeozones()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.all { it.isActive })
        assertEquals(1L, result[0].id)
        assertEquals(3L, result[1].id)
    }

    @Test
    fun `getActiveGeozones should return empty list when no active geozones`() {
        // Given
        val geozones = arrayOf(
            GeozoneDto(
                id = 1L,
                name = "Inactive Zone 1",
                type = "polygon",
                coordinates = listOf(listOf(1.0, 2.0)),
                isActive = false,
                s2Key = 1001L,
                lat = 55.7558,
                lon = 37.6173
            ),
            GeozoneDto(
                id = 2L,
                name = "Inactive Zone 2",
                type = "circle",
                coordinates = listOf(listOf(3.0, 4.0)),
                isActive = false,
                s2Key = 1002L,
                lat = 55.7559,
                lon = 37.6174
            )
        )

        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(geozones)

        // When
        val result = geozoneService.getActiveGeozones()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getGeozoneById should return geozone when found`() {
        // Given
        val geozones = arrayOf(
            GeozoneDto(
                id = 1L,
                name = "Zone 1",
                type = "polygon",
                coordinates = listOf(listOf(1.0, 2.0)),
                isActive = true,
                s2Key = 1001L,
                lat = 55.7558,
                lon = 37.6173
            ),
            GeozoneDto(
                id = 2L,
                name = "Zone 2",
                type = "circle",
                coordinates = listOf(listOf(3.0, 4.0)),
                isActive = false,
                s2Key = 1002L,
                lat = 55.7559,
                lon = 37.6174
            )
        )

        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(geozones)

        // When
        val result = geozoneService.getGeozoneById(2L)

        // Then
        assertEquals(2L, result?.id)
        assertEquals("Zone 2", result?.name)
    }

    @Test
    fun `getGeozoneById should return null when not found`() {
        // Given
        val geozones = arrayOf(
            GeozoneDto(
                id = 1L,
                name = "Zone 1",
                type = "polygon",
                coordinates = listOf(listOf(1.0, 2.0)),
                isActive = true,
                s2Key = 1001L,
                lat = 55.7558,
                lon = 37.6173
            )
        )

        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(geozones)

        // When
        val result = geozoneService.getGeozoneById(999L)

        // Then
        assertNull(result)
    }

    @Test
    fun `getGeozoneById should return null when no geozones available`() {
        // Given
        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenReturn(emptyArray())

        // When
        val result = geozoneService.getGeozoneById(1L)

        // Then
        assertNull(result)
    }

    @Test
    fun `getGeozoneById should return null when HTTP call fails`() {
        // Given
        whenever(restTemplate.getForObject("http://test-geozone-service/api/geozones", Array<GeozoneDto>::class.java))
            .thenThrow(RestClientException("Connection failed"))

        // When
        val result = geozoneService.getGeozoneById(1L)

        // Then
        assertNull(result)
    }
}