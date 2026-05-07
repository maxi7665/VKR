# Kafka Integration for Telemetry Processor

This document describes the Kafka integration implemented in the telemetry-processor Spring Boot application.

## Overview

The application now includes a Kafka consumer that:
- Listens to the `lynceus.prod.navigation` topic on `localhost:9092`
- Processes raw byte array messages (no deserialization)
- Passes messages to the `NavigationProcessor` component
- Logs received messages for debugging

## Components

### 1. NavigationProcessor (`src/main/kotlin/com/lynceus/telemetry_processor/processor/NavigationProcessor.kt`)
- Spring `@Component` that processes navigation messages
- Currently a stub implementation that logs message size and hex content
- Contains a `process(byte[])` method for future implementation

### 2. NavigationKafkaListener (`src/main/kotlin/com/lynceus/telemetry_processor/listener/NavigationKafkaListener.kt`)
- Spring `@Component` with `@KafkaListener` annotation
- Listens to the `lynceus.prod.navigation` topic
- Automatically starts when the application starts
- Passes raw byte arrays to `NavigationProcessor`

## Configuration

### Dependencies (build.gradle.kts)
Added Spring Kafka dependencies:
- `org.springframework.kafka:spring-kafka` for production
- `org.springframework.kafka:spring-kafka-test` for testing

### Application Properties (application.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: telemetry-processor-group
      key-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: true
```

## How It Works

1. When the Spring Boot application starts, Spring Kafka auto-configuration sets up a Kafka consumer
2. The `NavigationKafkaListener` bean is created and starts listening to the configured topic
3. When a message arrives on `lynceus.prod.navigation`:
   - The raw byte array is passed to the listener
   - The listener logs receipt and calls `navigationProcessor.process(message)`
   - The processor logs the message size and hex content
4. Messages are automatically committed based on the configuration

## Prerequisites

1. **Kafka Broker**: Must be running on `localhost:9092`
2. **Topic**: Create the topic if it doesn't exist:
   ```bash
   kafka-topics --create --topic lynceus.prod.navigation --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
   ```
3. **Java 17**: Required for Spring Boot 3.2.5

## Testing

### Send a Test Message
```bash
# Using kafka-console-producer with raw bytes
echo "test message" | kafka-console-producer --topic lynceus.prod.navigation --bootstrap-server localhost:9092
```

### Expected Log Output
When a message is received, you should see logs like:
```
INFO  NavigationKafkaListener - Received Kafka message from topic 'lynceus.prod.navigation'
INFO  NavigationProcessor - Received navigation message of size: 12 bytes
DEBUG NavigationProcessor - Message content (hex): 74657374206d657373616765
```

## Future Enhancements

1. **Actual Processing**: Implement real navigation processing logic in `NavigationProcessor`
2. **Error Handling**: Add error handling and dead letter queue for failed messages
3. **Metrics**: Add metrics and monitoring for message processing
4. **Configuration**: Make Kafka configuration externalizable (e.g., via environment variables)
5. **Testing**: Add unit and integration tests with embedded Kafka

## Troubleshooting

### Common Issues

1. **Kafka not running**: Ensure Kafka is running on `localhost:9092`
2. **Topic doesn't exist**: Create the topic before sending messages
3. **Deserialization errors**: The configuration uses `ByteArrayDeserializer` for raw bytes
4. **Group ID conflicts**: Change `group-id` if multiple instances are running

### Logging Configuration
To see debug logs (including hex content), set log level in `application.yaml`:
```yaml
logging:
  level:
    com.lynceus.telemetry_processor: DEBUG