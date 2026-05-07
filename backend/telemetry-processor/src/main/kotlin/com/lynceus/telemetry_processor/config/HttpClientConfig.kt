package com.lynceus.telemetry_processor.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(GeozoneProperties::class)
class HttpClientConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}