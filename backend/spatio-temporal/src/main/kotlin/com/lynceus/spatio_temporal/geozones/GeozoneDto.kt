package com.lynceus.spatio_temporal.geozones

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lynceus.spatio_temporal.geozones.entity.Geozone

class GeozoneDto(
    val id: Long? = null,
    val name: String = "",
    val type: String = "",
    val coordinates: List<List<Double>>,
    val isActive: Boolean = true,
    val s2Key: Long = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {

    companion object {
        fun fromGeozone(zone: Geozone): GeozoneDto {
            val mapper = jacksonObjectMapper()
            
            // Поле coordinates в Geozone хранится как JSON строка из PostgreSQL JSONB
            // Нужно корректно распарсить его в List<List<Double>>
            val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<List<Double>>>() {}
            val coordinates: List<List<Double>> = try {
                mapper.readValue(zone.coordinates, typeRef)
            } catch (e: Exception) {
                emptyList()
            }

            return GeozoneDto(
                id = zone.id,
                name = zone.name,
                type = zone.type,
                coordinates = coordinates,
                isActive = zone.isActive,
                s2Key = zone.s2Key,
                lat = zone.lat,
                lon = zone.lon
            )
        }
    }
}