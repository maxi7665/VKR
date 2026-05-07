package com.lynceus.telemetry_processor

import com.lynceus.telemetry_processor.processor.NavigationProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
class NavigationProcessorTest {

    @Autowired
    private lateinit var navigationProcessor: NavigationProcessor

    @Test
    fun `navigation processor bean is loaded`() {
        assertNotNull(navigationProcessor, "NavigationProcessor should be loaded as a Spring bean")
    }

    @Test
    fun `navigation processor processes byte array without error`() {
        val testData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        navigationProcessor.process(testData)
        // If no exception is thrown, the test passes
    }
}