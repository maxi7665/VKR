package com.lynceus.telemetry_processor.listener

import com.lynceus.telemetry_processor.processor.NavigationProcessor
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NavigationKafkaListener(
    private val navigationProcessor: NavigationProcessor
) {
    
    private val logger = LoggerFactory.getLogger(NavigationKafkaListener::class.java)
    
    @KafkaListener(topics = ["lynceus.prod.navigation"])
    fun listen(message: ByteArray) {
        //logger.info("Received Kafka message from topic 'lynceus.prod.navigation'")
        navigationProcessor.process(message)
    }
}