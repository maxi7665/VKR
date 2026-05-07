package com.lynceus.spatio_temporal

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication

@SpringBootApplication
class SpatioTemporalApplication

fun main(args: Array<String>) {

//	SpringApplicationBuilder(SpatioTemporalApplication::class.java)
//		.web(WebApplicationType.SERVLET) // Явно заставляем искать веб-сервер
//		.run(*args)


	runApplication<SpatioTemporalApplication>(*args)
}
