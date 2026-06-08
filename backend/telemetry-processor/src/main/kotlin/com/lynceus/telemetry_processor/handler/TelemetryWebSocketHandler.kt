package com.lynceus.telemetry_processor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.geometry.S2ContainsPointQuery
import com.google.common.geometry.S2LatLng
import com.google.common.geometry.S2Loop
import com.google.common.geometry.S2Polygon
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import com.google.common.geometry.S2ShapeIndex


@Component
class TelemetryWebSocketHandler(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val redisMessageListenerContainer: RedisMessageListenerContainer
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    // Сессии, подписанные на определённые ячейки
    private val sessionCellSubscriptions = ConcurrentHashMap<WebSocketSession, S2ContainsPointQuery>()

    // Обработчик сообщений из Redis keyspace notifications
    private val redisMessageListener = MessageListener { message, pattern ->

        if (pattern != null) {
            handleKeyspaceNotification(
                String(message.body),
                String(pattern)
            )
        }
    }

    // Pattern для подписки на set операции ключей nav:*:last
    companion object {
        const val KEYSPACE_PATTERN = "__keyevent@0__:set"
        const val NAV_KEY_PREFIX = "nav:"
        const val NAV_KEY_SUFFIX = ":last"
    }

    init {
        // Подписываемся на keyspace notifications для set операций
        redisMessageListenerContainer.addMessageListener(redisMessageListener, PatternTopic(KEYSPACE_PATTERN))
        logger.info("Redis keyspace notification listener registered for pattern: $KEYSPACE_PATTERN")
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("WebSocket connection established: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val payload = message.payload
            logger.debug("Received message from ${session.id}: $payload")
            
            val request = objectMapper.readTree(payload)
            val typeNode = request["type"]
            if (typeNode == null || typeNode.isNull) {
                session.sendMessage(TextMessage("""{"error": "Missing 'type' field"}"""))
                return
            }
            val type = typeNode.asText()
            if (type == null || type.isBlank()) {
                session.sendMessage(TextMessage("""{"error": "Missing 'type' field"}"""))
                return
            }
            
            when (type) {
                "subscribe_polygon" -> handleSubscribePolygon(session, request)
                else -> session.sendMessage(TextMessage("""{"error": "Unknown message type: $type"}"""))
            }
        } catch (e: Exception) {
            logger.error("Error processing WebSocket message", e)
            session.sendMessage(TextMessage("""{"error": "${e.message}"}"""))
        }
    }

    private fun handleSubscribePolygon(session: WebSocketSession, request: com.fasterxml.jackson.databind.JsonNode) {
        val pointsNode = request["points"]
        if (pointsNode == null || pointsNode.isNull) {
            session.sendMessage(TextMessage("""{"error": "Missing 'points' field"}"""))
            return
        }
        if (!pointsNode.isArray) {
            session.sendMessage(TextMessage("""{"error": "Points must be an array"}"""))
            return
        }
        if (pointsNode.size() < 3) {
            session.sendMessage(TextMessage("""{"error": "Polygon must have at least 3 points"}"""))
            return
        }
        
        val points = mutableListOf<Pair<Double, Double>>()
        for (point in pointsNode) {
            val lat = point["lat"].asDouble()
            val lon = point["lon"].asDouble()
            points.add(lat to lon)
        }

        val s2Points = points.map { S2LatLng.fromDegrees(
            it.first,
            it.second).toPoint() }
        val loop = S2Loop(s2Points)
        val polygon = S2Polygon(loop)

        val index = S2ShapeIndex()
        index.add(polygon.shape())

        // Создаем объект запроса
        // Reuse этого объекта важен для производительности (кэширование)
        val query = S2ContainsPointQuery(index)

        // Очищаем предыдущие подписки сессии
        sessionCellSubscriptions.remove(session)

        // новая подписка
        sessionCellSubscriptions[session] = query
        
        // Для каждой ячейки получаем устройства
        val allDeviceIds = mutableSetOf<Long>()
        val options = ScanOptions.scanOptions()
            .match("nav:*:last") // Задаем паттерн (например, "myKey*")
            .build()

        // ищем все ключи навигации
        val cursor = redisTemplate.scan(options)
        val keys = mutableListOf<String>()
        
        if (cursor != null) {
            while (cursor.hasNext()) {
                val key = cursor.next()
                keys.add(key)
            }
        }
        
        // берем всю навигацию
        val data = if (keys.isNotEmpty()) redisTemplate.opsForValue().multiGet(keys) else null

        // если навигация входит в точку, отдаем клиенту (должно быть быстро)
        if (data != null) {
            for (value in data) {
                if (value != null) {
                    val str = value.toString()
                    try {
                        val obj = objectMapper.readValue(str, TelemetryPacket::class.java)
                        val s2Point = S2LatLng.fromDegrees(obj.latitude, obj.longitude).toPoint()
                        //session.sendMessage(TextMessage(str))
                        if (query.contains(s2Point)) {
                            session.sendMessage(TextMessage(str))
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to parse telemetry data: $str", e)
                    }
                }
            }
        }
        
        session.sendMessage(TextMessage("""{"status": "subscribed"}"""))
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleKeyspaceNotification(message: String, pattern: String) {
        //logger.info("Received Redis keyspace notification on pattern $pattern: $message")

        if (sessionCellSubscriptions.isEmpty()) {
            return
        }

        // message содержит ключ, который был изменен (например "nav:123:last")
        if (!message.startsWith(NAV_KEY_PREFIX) || !message.endsWith(NAV_KEY_SUFFIX)) {
            return // Нас интересуют только ключи nav:*:last
        }
        
        try {
            // Извлекаем deviceId из ключа
            val deviceId = extractDeviceIdFromKey(message)
            if (deviceId == null) {
                logger.warn("Failed to extract deviceId from key: $message")
                return
            }
            
            // Получаем обновленные данные из Redis
            val data = redisTemplate.opsForValue().get(message) as? String
            if (data == null) {
                logger.warn("No telemetry data found for key: $message")
                return
            }
            
            val telemetryPacket = objectMapper.readValue(
                data,
                TelemetryPacket::class.java)
            val s2Point = S2LatLng.fromDegrees(
                telemetryPacket.latitude,
                telemetryPacket.longitude).toPoint()
            
            // Находим сессии, подписанные на эту ячейку
            val sessions = sessionCellSubscriptions.filter { it.value.contains(s2Point) }.keys
            if (sessions.isEmpty()) {
                //logger.debug("No sessions subscribed to cell $s2Cell for device $deviceId")
                return
            }
            
            //logger.debug("Sending update to ${sessions.size} sessions for device $deviceId in cell $s2Cell")
            
            // Отправляем обновление всем подписанным сессиям
            for (session in sessions) {
                if (session.isOpen) {
                    try {
                        session.sendMessage(TextMessage(data))
                    } catch (e: Exception) {
                        logger.error("Failed to send telemetry update to session ${session.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process keyspace notification for key: $message", e)
        }
    }
    
    fun extractDeviceIdFromKey(key: String): Long? {
        // key format: "nav:{deviceId}:last"
        val regex = "^nav:(\\d+):last$".toRegex()
        return regex.find(key)?.groupValues?.get(1)?.toLongOrNull()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("WebSocket connection closed: ${session.id}, status: $status")
        // Удаляем подписки сессии
        sessionCellSubscriptions.remove(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Transport error for session ${session.id}", exception)
    }
}