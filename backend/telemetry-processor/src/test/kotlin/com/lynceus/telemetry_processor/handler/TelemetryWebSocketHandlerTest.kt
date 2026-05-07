package com.lynceus.telemetry_processor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
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
    private lateinit var setOps: SetOperations<String, Any>

    @Mock
    private lateinit var valueOps: ValueOperations<String, Any>

    @Captor
    private lateinit var textMessageCaptor: ArgumentCaptor<TextMessage>

    private lateinit var handler: TelemetryWebSocketHandler

    @BeforeEach
    fun setUp() {
        handler = TelemetryWebSocketHandler(redisTemplate, objectMapper, redisMessageListenerContainer)
        `when`(redisTemplate.opsForSet()).thenReturn(setOps)
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
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

        // Mock session subscribed to the cell
        val sessionCellSubscriptions = getPrivateField<ConcurrentHashMap<WebSocketSession, MutableSet<Long>>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = mutableSetOf(s2Cell, 1002L)

        `when`(session.isOpen).thenReturn(true)
        `when`(valueOps.get(key)).thenReturn(telemetryData)
        `when`(objectMapper.readValue(telemetryData, TelemetryPacket::class.java)).thenReturn(telemetryPacket)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session).sendMessage(textMessageCaptor.capture())
        val sentMessage = textMessageCaptor.value.payload
        assert(sentMessage == telemetryData)
    }

    @Test
    fun `should ignore keyspace notification for non-nav key`() {
        // Given
        val key = "cell:1001:devices"

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verifyNoInteractions(session, valueOps, objectMapper)
    }

    @Test
    fun `should ignore keyspace notification when no session subscribed`() {
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

        // No sessions subscribed
        val sessionCellSubscriptions = getPrivateField<ConcurrentHashMap<WebSocketSession, MutableSet<Long>>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions.clear()

        `when`(valueOps.get(key)).thenReturn(telemetryData)
        `when`(objectMapper.readValue(telemetryData, TelemetryPacket::class.java)).thenReturn(telemetryPacket)

        // When
        handler.handleKeyspaceNotification(key, "__keyevent@0__:set")

        // Then
        verify(session, never()).sendMessage(any())
    }

    @Test
    fun `should remove session subscriptions on connection closed`() {
        // Given
        val sessionCellSubscriptions = getPrivateField<ConcurrentHashMap<WebSocketSession, MutableSet<Long>>>(
            handler, "sessionCellSubscriptions"
        )
        sessionCellSubscriptions[session] = mutableSetOf(1001L, 1002L)

        // When
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // Then
        assert(sessionCellSubscriptions.isEmpty())
    }

    @Test
    fun `should handle transport error`() {
        // Given
        val exception = RuntimeException("Test error")

        // When
        handler.handleTransportError(session, exception)

        // Then - just verify no exception is thrown
        // Logging is tested indirectly
    }

    // Helper method to access private fields for testing
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(obj: Any, fieldName: String): T {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj) as T
    }
}