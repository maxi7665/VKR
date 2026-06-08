package com.lynceus.telemetry_processor.service

import com.lynceus.telemetry_processor.config.GeozoneProperties
import com.lynceus.telemetry_processor.dto.GeozoneDto
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject

@Component
class GeozoneService(
    private val restTemplate: RestTemplate,
    private val geozoneProperties: GeozoneProperties
) {
    private val logger = LoggerFactory.getLogger(GeozoneService::class.java)

    /**
     * Получить все геозоны из внешнего сервиса.
     * @return список геозон или пустой список в случае ошибки
     */
    fun getAllGeozones(): List<GeozoneDto> {
        val url = geozoneProperties.service.url
        return try {
            val geozones = restTemplate.getForObject<Array<GeozoneDto>?>(url)
            geozones?.toList() ?: emptyList()
        } catch (e: RestClientException) {
            logger.error("Ошибка при получении геозон с URL: $url", e)
            emptyList()
        }
    }

    /**
     * Получить активные геозоны.
     * @return список активных геозон
     */
    fun getActiveGeozones(): List<GeozoneDto> {
        return getAllGeozones().filter { it.isActive }
    }

    /**
     * Получить геозону по идентификатору.
     * @param id идентификатор геозоны
     * @return геозона или null, если не найдена
     */
    fun getGeozoneById(id: Long): GeozoneDto? {
        return getAllGeozones().firstOrNull { it.id == id }
    }
}