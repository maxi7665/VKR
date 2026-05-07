package com.lynceus.telemetry_processor.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import com.lynceus.telemetry_processor.handler.TelemetryWebSocketHandler
import com.lynceus.telemetry_processor.handler.ZoneVisitWebSocketHandler

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val telemetryWebSocketHandler: TelemetryWebSocketHandler,
    private val zoneVisitWebSocketHandler: ZoneVisitWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(telemetryWebSocketHandler, "/ws/telemetry")
            .setAllowedOrigins("*")
        registry.addHandler(zoneVisitWebSocketHandler, "/ws/zone-visits")
            .setAllowedOrigins("*")
    }
}