package com.lynceus.telemetry_processor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TelemetryProcessorApplication

fun main(args: Array<String>) {
	runApplication<TelemetryProcessorApplication>(*args)
}
