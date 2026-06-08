package com.lynceus.telemetry_processor.listener

import com.lynceus.telemetry_processor.processor.NavigationProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class NavigationKafkaListenerTest {

    @Mock
    private lateinit var navigationProcessor: NavigationProcessor

    @Test
    fun `listen should call navigationProcessor process with same byte array`() {
        // Given
        val listener = NavigationKafkaListener(navigationProcessor)
        val message = byteArrayOf(1, 2, 3, 4)

        // When
        listener.listen(message)

        // Then
        verify(navigationProcessor).process(message)
    }

    @Test
    fun `listen should handle empty byte array`() {
        // Given
        val listener = NavigationKafkaListener(navigationProcessor)
        val emptyMessage = byteArrayOf()

        // When
        listener.listen(emptyMessage)

        // Then
        verify(navigationProcessor).process(emptyMessage)
    }

    @Test
    fun `listen should call process exactly once`() {
        // Given
        val listener = NavigationKafkaListener(navigationProcessor)
        val message = byteArrayOf(10, 20, 30)

        // When
        listener.listen(message)

        // Then
        verify(navigationProcessor).process(message)
        // verify no more interactions
        // (можно добавить verifyNoMoreInteractions, но в данном случае это излишне)
    }
}