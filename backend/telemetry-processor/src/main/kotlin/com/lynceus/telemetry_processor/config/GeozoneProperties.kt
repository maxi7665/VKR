package com.lynceus.telemetry_processor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "geozone")
data class GeozoneProperties @ConstructorBinding constructor(
    val service: ServiceProperties
) {
    data class ServiceProperties(
        val url: String
    )
}