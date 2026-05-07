package com.lynceus.telemetry_processor.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import java.time.Duration

@Configuration
@EnableKafka
class KafkaConfig {

    @Bean
    fun consumerFactory(): ConsumerFactory<ByteArray, ByteArray> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ConsumerConfig.GROUP_ID_CONFIG to "telemetry-processor-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<ByteArray, ByteArray> {
        val factory = ConcurrentKafkaListenerContainerFactory<ByteArray, ByteArray>()
        factory.consumerFactory = consumerFactory()
        return factory
    }

    /**
     * Создаем и контролируем топик под события посещений зон
     */
    @Bean
    fun zoneVisitEventTopic(): NewTopic {
        return TopicBuilder.name("lynceus.prod.zone-visit")
            .config(
                "retention.ms",
                Duration.ofDays(1).toMillis().toString())
            .build()
    }
}