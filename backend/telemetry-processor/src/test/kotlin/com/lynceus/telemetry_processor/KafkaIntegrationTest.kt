package com.lynceus.telemetry_processor

import com.lynceus.telemetry_processor.processor.NavigationProcessor
import com.lynceus.telemetry_processor.listener.NavigationKafkaListener
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["lynceus.prod.navigation"],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class KafkaIntegrationTest {

    @Autowired(required = false)
    private lateinit var navigationProcessor: NavigationProcessor

    @Autowired(required = false)
    private lateinit var navigationKafkaListener: NavigationKafkaListener

    @Test
    fun `context loads successfully`() {
        // Verify that Spring context loads without errors
        assertNotNull(navigationProcessor, "NavigationProcessor should be loaded")
        assertNotNull(navigationKafkaListener, "NavigationKafkaListener should be loaded")
    }

    @Test
    fun `navigation processor processes byte array`() {
        // Simple test to verify the processor can be called
        val testData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        navigationProcessor.process(testData)
        // If no exception is thrown, the test passes
    }
}