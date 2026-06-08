package com.lynceus.telemetry_processor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.lynceus.telemetry_processor.event.InOut
import com.lynceus.telemetry_processor.event.ZoneVisitEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZoneVisitWebSocketHandlerTest {

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var session1: WebSocketSession

    @Mock
    private lateinit var session2: WebSocketSession

    private lateinit var handler: ZoneVisitWebSocketHandler

    @BeforeEach
    fun setUp() {
        handler = ZoneVisitWebSocketHandler(objectMapper)
    }

    @Test
    fun `should add session to sessions set when connection established`() {
        // Given
        val sessionId = "test-session-1"
        whenever(session1.id).thenReturn(sessionId)

        // When
        handler.afterConnectionEstablished(session1)

        // Then - verify session is added (we'll test by checking broadcast works)
        // We'll verify in broadcast test
    }

    @Test
    fun `should remove session from sessions set when connection closed`() {
        // Given
        val sessionId = "test-session-1"
        whenever(session1.id).thenReturn(sessionId)
        
        // First establish connection
        handler.afterConnectionEstablished(session1)

        // When
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL)

        // Then - verify session is removed by checking broadcast doesn't send to it
        val event = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        val eventJson = """{"inOut":"In","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:00:00"}"""
        whenever(objectMapper.writeValueAsString(event)).thenReturn(eventJson)
        whenever(session1.isOpen).thenReturn(false)

        // Trigger broadcast
        handler.handleZoneVisitEvent(event)

        // Verify no message sent to closed session
        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `should broadcast event to all open sessions`() {
        // Given
        whenever(session1.id).thenReturn("session-1")
        whenever(session2.id).thenReturn("session-2")
        whenever(session1.isOpen).thenReturn(true)
        whenever(session2.isOpen).thenReturn(true)

        handler.afterConnectionEstablished(session1)
        handler.afterConnectionEstablished(session2)

        val event = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        val eventJson = """{"inOut":"In","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:00:00"}"""
        whenever(objectMapper.writeValueAsString(event)).thenReturn(eventJson)

        // When
        handler.handleZoneVisitEvent(event)

        // Then
        verify(session1).sendMessage(any())
        verify(session2).sendMessage(any())
        
        // Capture arguments to verify content
        val captor = argumentCaptor<TextMessage>()
        verify(session1).sendMessage(captor.capture())
        verify(session2).sendMessage(captor.capture())
        
        val capturedMessages = captor.allValues
        assert(capturedMessages.size == 2)
        assert(capturedMessages.all { it.payload == eventJson })
    }

    @Test
    fun `should remove closed sessions during broadcast`() {
        // Given
        whenever(session1.id).thenReturn("session-1")
        whenever(session2.id).thenReturn("session-2")
        whenever(session1.isOpen).thenReturn(false) // Session 1 is closed
        whenever(session2.isOpen).thenReturn(true)  // Session 2 is open

        handler.afterConnectionEstablished(session1)
        handler.afterConnectionEstablished(session2)

        val event = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        val eventJson = """{"inOut":"In","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:00:00"}"""
        whenever(objectMapper.writeValueAsString(event)).thenReturn(eventJson)

        // When
        handler.handleZoneVisitEvent(event)

        // Then
        // Should send only to session2
        verify(session1, never()).sendMessage(any())
        verify(session2).sendMessage(any())
        
        // Session1 should be removed from sessions set
        // We can verify by sending another event
        handler.handleZoneVisitEvent(event)
        verify(session1, never()).sendMessage(any()) // Still never called
        verify(session2, times(2)).sendMessage(any()) // Called twice
    }

    @Test
    fun `should handle transport error without exception`() {
        // Given
        val exception = RuntimeException("Test transport error")
        whenever(session1.id).thenReturn("session-1")

        // When
        handler.handleTransportError(session1, exception)

        // Then - just verify no exception is thrown
        // Logging is tested indirectly
    }

    @Test
    fun `should handle JSON serialization error during broadcast gracefully`() {
        // Given
        whenever(session1.id).thenReturn("session-1")
        whenever(session1.isOpen).thenReturn(true)

        handler.afterConnectionEstablished(session1)

        val event = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        whenever(objectMapper.writeValueAsString(event)).thenThrow(RuntimeException("JSON serialization error"))

        // When
        handler.handleZoneVisitEvent(event)

        // Then - should not throw exception, session should be removed due to error
        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `should handle send message error during broadcast gracefully`() {
        // Given
        whenever(session1.id).thenReturn("session-1")
        whenever(session1.isOpen).thenReturn(true)

        handler.afterConnectionEstablished(session1)

        val event = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        val eventJson = """{"inOut":"In","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:00:00"}"""
        whenever(objectMapper.writeValueAsString(event)).thenReturn(eventJson)
        whenever(session1.sendMessage(any())).thenThrow(RuntimeException("Send failed"))

        // When
        handler.handleZoneVisitEvent(event)

        // Then - should not throw exception, session should be removed due to error
        // Verify send was attempted
        verify(session1).sendMessage(any())
        
        // Session should be removed, so second event won't be sent to it
        handler.handleZoneVisitEvent(event)
        verify(session1, times(1)).sendMessage(any()) // Only called once
    }

    @Test
    fun `should handle both In and Out event types`() {
        // Given
        whenever(session1.id).thenReturn("session-1")
        whenever(session1.isOpen).thenReturn(true)

        handler.afterConnectionEstablished(session1)

        val inEvent = ZoneVisitEvent(
            inOut = InOut.In,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        val outEvent = ZoneVisitEvent(
            inOut = InOut.Out,
            deviceId = 123L,
            vehicleId = 456L,
            zoneId = 789L,
            zoneName = "Test Zone",
            zoneDateTime = LocalDateTime.now()
        )
        
        val inJson = """{"inOut":"In","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:00:00"}"""
        val outJson = """{"inOut":"Out","deviceId":123,"vehicleId":456,"zoneId":789,"zoneName":"Test Zone","zoneDateTime":"2023-01-01T12:05:00"}"""
        
        whenever(objectMapper.writeValueAsString(inEvent)).thenReturn(inJson)
        whenever(objectMapper.writeValueAsString(outEvent)).thenReturn(outJson)

        // When
        handler.handleZoneVisitEvent(inEvent)
        handler.handleZoneVisitEvent(outEvent)

        // Then
        verify(session1, times(2)).sendMessage(any())
        
        val captor = argumentCaptor<TextMessage>()
        verify(session1, times(2)).sendMessage(captor.capture())
        
        val capturedMessages = captor.allValues
        assert(capturedMessages[0].payload == inJson)
        assert(capturedMessages[1].payload == outJson)
    }
}