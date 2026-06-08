package com.lynceus.telemetry_processor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.geometry.S2ContainsPointQuery
import com.google.common.geometry.S2LatLng
import com.google.common.geometry.S2Point 
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelemetryWebSocketHandlerTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    @Mock
    private lateinit var session: WebSocketSession

    @Mock
    private lateinit var session2: WebSocketSession

    @Mock
    private lateinit var setOps: SetOperations<String, Any>

    @Mock
    private lateinit var valueOps: ValueOperations<String, Any>

    private lateinit var handler: TelemetryWebSocketHandler

    @BeforeEach
    fun setUp() {
        handler = TelemetryWebSocketHandler(redisTemplate, objectMapper, redisMessageListenerContainer)
        whenever(redisTemplate.opsForSet()).thenReturn(setOps)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    fun `should extract deviceId from nav key correctly`() {
        // Given
        val key1 = "nav:123:last"
        val key2 = "nav:456:last"
        val key3 = "invalid:key"

        // When & Then
        assert(handler.extractDeviceIdFromKey(key1) == 123L)
        assert(handler.extractDeviceIdFromKey(key2) == 456L)
        assert(handler.extractDeviceIdFromKey(key3) == null)
    }

    @Test
    fun `should handle keyspace notification for subscribed cell`() {
        // Given
        val deviceId = 123L
        val s2Cell = 1001L
        val key = "nav:$deviceId:last"
        val telemetryData = """{"deviceId":$deviceId,"s2Cell":$s2Cell}"""
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = s2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        // Mock S2ContainsPointQuery that always returns true for any point
        val query = mock<S2ContainsPointQuery>()
        whenever(query.contains(any())).thenReturn(true)

        // Add session subscription with the mock query
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // Mock Redis value operations to return telemetry data
        whenever(valueOps.get(key)).thenReturn(telemetryData)
        whenever(objectMapper.readValue(telemetryData, TelemetryPacket::class.java)).thenReturn(telemetryPacket)
        // Mock session as open
        whenever(session.isOpen).thenReturn(true)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session).sendMessage(argThat { message ->
            (message as TextMessage).payload.contains("""{"deviceId":$deviceId,"s2Cell":$s2Cell}""")
        })
    }

    @Test
    fun `should ignore keyspace notification for non-subscribed cell`() {
        // Given
        val deviceId = 123L
        val s2Cell = 1001L
        val key = "nav:$deviceId:last"
        val telemetryData = """{"deviceId":$deviceId,"s2Cell":$s2Cell}"""
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = s2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        // Mock S2ContainsPointQuery that always returns false for any point
        val query = mock<S2ContainsPointQuery>()
        whenever(query.contains(any())).thenReturn(false)

        // Add session subscription with the mock query
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // Mock Redis value operations to return telemetry data
        whenever(valueOps.get(key)).thenReturn(telemetryData)
        whenever(objectMapper.readValue(telemetryData, TelemetryPacket::class.java)).thenReturn(telemetryPacket)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification when Redis returns null`() {
        // Given
        val deviceId = 123L
        val key = "nav:$deviceId:last"

        // Mock S2ContainsPointQuery that always returns true
        val query = mock<S2ContainsPointQuery>()
        whenever(query.contains(any())).thenReturn(true)

        // Add session subscription
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // Mock Redis to return null
        whenever(valueOps.get(key)).thenReturn(null)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification with JSON parsing error`() {
        // Given
        val deviceId = 123L
        val key = "nav:$deviceId:last"
        val telemetryData = """{"deviceId":$deviceId,"s2Cell":1001}"""

        // Mock S2ContainsPointQuery that always returns true
        val query = mock<S2ContainsPointQuery>()
        whenever(query.contains(any())).thenReturn(true)

        // Add session subscription
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // Mock Redis to return data but objectMapper throws exception
        whenever(valueOps.get(key)).thenReturn(telemetryData)
        whenever(objectMapper.readValue(telemetryData, TelemetryPacket::class.java))
            .thenThrow(RuntimeException("JSON parsing error"))

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification for multiple sessions`() {
        // Given
        val deviceId = 123L
        val s2Cell = 1001L
        val key = "nav:$deviceId:last"
        val telemetryData = """{"deviceId":$deviceId,"s2Cell":$s2Cell}"""
        
        val telemetryPacket = TelemetryPacket(
            deviceId = deviceId,
            vehicleId = 1L,
            latitude = 55.7558,
            longitude = 37.6173,
            s2Cell = s2Cell,
            azimuth = 90,
            packetTime = LocalDateTime.now()
        )

        // Mock queries for both sessions
        val query1 = mock<S2ContainsPointQuery>()
        whenever(query1.contains(any())).thenReturn(true)
        
        val query2 = mock<S2ContainsPointQuery>()
        whenever(query2.contains(any())).thenReturn(false)

        // Add both session subscriptions
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query1
        sessionCellSubscriptions[session2] = query2

        // Mock Redis
        whenever(valueOps.get(key)).thenReturn(telemetryData)
        whenever(objectMapper.readValue(telemetryData, TelemetryPacket::class.java)).thenReturn(telemetryPacket)

        // Ensure sessions are open
        whenever(session.isOpen).thenReturn(true)
        whenever(session2.isOpen).thenReturn(true)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session).sendMessage(any())
        verify(session2, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification for non-nav key`() {
        // Given
        val key = "other:123:last"

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(valueOps, never()).get(any())
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification with malformed key`() {
        // Given
        val key = "nav:not-a-number:last"

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(valueOps, never()).get(any())
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification with empty key`() {
        // Given
        val key = ""

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(valueOps, never()).get(any())
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle keyspace notification for different Redis event type`() {
        // Given
        val deviceId = 123L
        val key = "nav:$deviceId:last"

        // When
        handler.handleKeyspaceNotification("__keyevent@0__:del", key)

        // Then
        verify(valueOps, never()).get(any())
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should handle subscribe_polygon message`() {
        // Given
        val jsonNode = createPolygonJsonNode()
        val message = TextMessage("""{"type": "subscribe_polygon", "points": [{"lat": 55.7558, "lon": 37.6173}, {"lat": 55.7559, "lon": 37.6174}, {"lat": 55.7560, "lon": 37.6175}]}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)
        
        // Mock Redis scan to return empty cursor
        val mockCursor = mock<org.springframework.data.redis.core.Cursor<String>> {
            on { hasNext() } doReturn false
        }
        whenever(redisTemplate.scan(any<org.springframework.data.redis.core.ScanOptions>())).thenReturn(mockCursor)
        whenever(valueOps.multiGet(any())).thenReturn(emptyList<Any>())

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        assert(sessionCellSubscriptions.containsKey(session))
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"status": "subscribed"}""")
        })
    }

    @Test
    fun `should handle subscribe_polygon with insufficient points`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn "subscribe_polygon"
        }
        val pointsArray = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { isArray } doReturn true
            on { size() } doReturn 2
            on { iterator() } doReturn mutableListOf<com.fasterxml.jackson.databind.JsonNode>().iterator()
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
            on { get("points") } doReturn pointsArray
        }
        val message = TextMessage("""{"type": "subscribe_polygon", "points": [{"lat": 55.7558, "lon": 37.6173}, {"lat": 55.7559, "lon": 37.6174}]}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Polygon must have at least 3 points"}""")
        })
    }

    @Test
    fun `should handle subscribe_polygon with invalid points format`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn "subscribe_polygon"
        }
        val pointsNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { isArray } doReturn false
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
            on { get("points") } doReturn pointsNode
        }
        val message = TextMessage("""{"type": "subscribe_polygon", "points": "not an array"}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Points must be an array"}""")
        })
    }

    @Test
    fun `should handle unknown message type`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn "unknown_type"
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
        }
        val message = TextMessage("""{"type": "unknown_type"}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Unknown message type: unknown_type"}""")
        })
    }

    @Test
    fun `should handle JSON parsing error in handleTextMessage`() {
        // Given
        val message = TextMessage("invalid json")
        whenever(objectMapper.readTree("invalid json"))
            .thenThrow(RuntimeException("JSON parsing error"))

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            val payload = (msg as TextMessage).payload
            payload.contains("""{"error": "JSON parsing error"}""") || payload.contains("""{"error":""")
        })
    }

    @Test
    fun `should handle missing type field`() {
        // Given
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn null
        }
        val message = TextMessage("""{"data": "some data"}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Missing 'type' field"}""")
        })
    }

    @Test
    fun `should handle null type field value`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn null
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
        }
        val message = TextMessage("""{"type": null}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Missing 'type' field"}""")
        })
    }

    @Test
    fun `should handle empty type field`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn ""
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
        }
        val message = TextMessage("""{"type": ""}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Missing 'type' field"}""")
        })
    }

    @Test
    fun `should handle subscribe_polygon with empty points array`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn "subscribe_polygon"
        }
        val pointsArray = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { isArray } doReturn true
            on { size() } doReturn 0
            on { iterator() } doReturn mutableListOf<com.fasterxml.jackson.databind.JsonNode>().iterator()
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
            on { get("points") } doReturn pointsArray
        }
        val message = TextMessage("""{"type": "subscribe_polygon", "points": []}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Polygon must have at least 3 points"}""")
        })
    }

    @Test
    fun `should handle subscribe_polygon with null points`() {
        // Given
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { asText() } doReturn "subscribe_polygon"
        }
        val jsonNode = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
            on { get("points") } doReturn null
        }
        val message = TextMessage("""{"type": "subscribe_polygon"}""")
        whenever(objectMapper.readTree(message.payload)).thenReturn(jsonNode)

        // When
        callProtectedHandleTextMessage(handler, session, message)

        // Then
        verify(session).sendMessage(argThat { msg ->
            (msg as TextMessage).payload.contains("""{"error": "Missing 'points' field"}""")
        })
    }

    @Test
    fun `should handle afterConnectionClosed by removing subscriptions`() {
        // Given
        val query = mock<S2ContainsPointQuery>()
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // When
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // Then
        assert(!sessionCellSubscriptions.containsKey(session))
    }

    @Test
    fun `should handle afterConnectionClosed when session not subscribed`() {
        // Given - no subscription for this session

        // When
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // Then - should not throw exception
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        assert(!sessionCellSubscriptions.containsKey(session))
    }

    @Test
    fun `should handle afterConnectionClosed with multiple sessions`() {
        // Given
        val query1 = mock<S2ContainsPointQuery>()
        val query2 = mock<S2ContainsPointQuery>()
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query1
        sessionCellSubscriptions[session2] = query2

        // When
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // Then
        assert(!sessionCellSubscriptions.containsKey(session))
        assert(sessionCellSubscriptions.containsKey(session2))
    }

    @Test
    fun `should handle afterConnectionEstablished`() {
        // When
        handler.afterConnectionEstablished(session)

        // Then - should not throw exception, just log
        // No assertions needed for logging
    }

    @Test
    fun `should handle constructor initialization`() {
        // Given - handler created in setUp()

        // When & Then - verify Redis listener is registered
        verify(redisMessageListenerContainer).addMessageListener(any<org.springframework.data.redis.connection.MessageListener>(), any<org.springframework.data.redis.listener.PatternTopic>())
    }

    @Test
    fun `should handle malformed nav keys in extractDeviceIdFromKey`() {
        // Given
        val malformedKeys = listOf(
            "nav:",
            "nav::last",
            "nav:abc:last",
            "nav:123",
            "nav:123:",
            ":123:last",
            ""
        )

        // When & Then
        malformedKeys.forEach { key ->
            assert(handler.extractDeviceIdFromKey(key) == null)
        }
    }

    @Test
    fun `should handle keyspace notification with Redis connection error`() {
        // Given
        val deviceId = 123L
        val key = "nav:$deviceId:last"

        // Mock S2ContainsPointQuery
        val query = mock<S2ContainsPointQuery>()
        whenever(query.contains(any())).thenReturn(true)

        // Add session subscription
        val sessionCellSubscriptions = getPrivateField<
                ConcurrentHashMap<
                        WebSocketSession,
                        S2ContainsPointQuery>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = query

        // Mock Redis to throw exception
        whenever(valueOps.get(key)).thenThrow(RuntimeException("Redis connection error"))

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session, never()).sendMessage(any())
    }

    private fun createPolygonJsonNode(): com.fasterxml.jackson.databind.JsonNode {
        val latNode1 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 55.7558 }
        val lonNode1 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 37.6173 }
        val point1 = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("lat") } doReturn latNode1
            on { get("lon") } doReturn lonNode1
        }
        val latNode2 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 55.7559 }
        val lonNode2 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 37.6174 }
        val point2 = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("lat") } doReturn latNode2
            on { get("lon") } doReturn lonNode2
        }
        val latNode3 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 55.7560 }
        val lonNode3 = mock<com.fasterxml.jackson.databind.JsonNode> { on { asDouble() } doReturn 37.6175 }
        val point3 = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("lat") } doReturn latNode3
            on { get("lon") } doReturn lonNode3
        }
        
        val pointsArray = mock<com.fasterxml.jackson.databind.JsonNode> {
            on { isArray } doReturn true
            on { size() } doReturn 3
            on { iterator() } doReturn mutableListOf(point1, point2, point3).iterator()
        }
        
        val typeNode = mock<com.fasterxml.jackson.databind.JsonNode> { on { asText() } doReturn "subscribe_polygon" }
        return mock<com.fasterxml.jackson.databind.JsonNode> {
            on { get("type") } doReturn typeNode
            on { get("points") } doReturn pointsArray
        }
    }

    // Helper method to call protected handleTextMessage using reflection
    private fun callProtectedHandleTextMessage(
        handler: TelemetryWebSocketHandler,
        session: WebSocketSession,
        message: TextMessage
    ) {
        val method = TelemetryWebSocketHandler::class.java.getDeclaredMethod(
            "handleTextMessage",
            WebSocketSession::class.java,
            TextMessage::class.java
        )
        method.isAccessible = true
        method.invoke(handler, session, message)
    }

    // Helper method to access private fields for testing
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }
}