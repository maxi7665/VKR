package com.lynceus.telemetry_processor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.lynceus.telemetry_processor.event.ZoneVisitEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class ZoneVisitWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("ZoneVisit WebSocket connection established: ${session.id}")
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("ZoneVisit WebSocket connection closed: ${session.id}, status: $status")
        sessions.remove(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Transport error for session ${session.id}", exception)
    }

    @EventListener
    fun handleZoneVisitEvent(event: ZoneVisitEvent) {
        logger.debug("Received ZoneVisitEvent: $event")
        broadcastEvent(event)
    }

    private fun broadcastEvent(event: ZoneVisitEvent) {
        try {
            val json = objectMapper.writeValueAsString(event)
            val message = TextMessage(json)

            val iterator = sessions.iterator()
            while (iterator.hasNext()) {
                val session = iterator.next()
                try {
                    if (session.isOpen) {
                        session.sendMessage(message)
                    } else {
                        iterator.remove()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send ZoneVisitEvent to session ${session.id}", e)
                    iterator.remove()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to serialize ZoneVisitEvent: $event", e)
        }
    }
}