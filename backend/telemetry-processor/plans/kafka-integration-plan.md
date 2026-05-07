# Kafka Integration Plan for Telemetry Processor

## Overview
This document outlines the plan to integrate Kafka consumer functionality into the Spring Boot Kotlin application to listen to the `lynceus.prod.navigation` topic on `localhost:9092` and process raw byte array messages.

## Current Project Analysis
- **Project**: Kotlin Spring Boot application
- **Spring Boot Version**: Currently 4.0.6 (needs correction to 3.2.5)
- **Java Version**: 17
- **Current Dependencies**: Spring Web MVC, Kotlin reflect, Tomcat runtime
- **Project Structure**: Basic Spring Boot application with minimal configuration

## Requirements
1. Listen to Kafka topic `lynceus.prod.navigation` on `localhost:9092`
2. Use Spring Kafka with auto-start consumer
3. Process raw byte arrays (no deserialization)
4. Pass messages to `NavigationProcessor` component with `process(byte[])` method
5. Log received messages
6. NavigationProcessor should be a stub initially

## Implementation Plan

### 1. Update Dependencies (build.gradle.kts)
```kotlin
// Update Spring Boot version from 4.0.6 to 3.2.5
id("org.springframework.boot") version "3.2.5"

// Add Spring Kafka dependency
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.kafka:spring-kafka")  // Add this
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")  // Add for testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

### 2. Configure Kafka Properties (application.yaml)
```yaml
spring:
  application:
    name: telemetry-processor
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: telemetry-processor-group
      key-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: true
```

### 3. Create NavigationProcessor Component
**Location**: `src/main/kotlin/com/lynceus/telemetry_processor/processor/NavigationProcessor.kt`

```kotlin
package com.lynceus.telemetry_processor.processor

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NavigationProcessor {
    
    private val logger = LoggerFactory.getLogger(NavigationProcessor::class.java)
    
    fun process(message: ByteArray) {
        logger.info("Received navigation message of size: ${message.size} bytes")
        // Stub implementation - just log the message
        logger.debug("Message content (hex): ${message.joinToString("") { "%02x".format(it) }}")
        
        // TODO: Implement actual navigation processing logic
        // For now, this is a stub that does nothing
    }
}
```

### 4. Create Kafka Consumer Configuration
**Location**: `src/main/kotlin/com/lynceus/telemetry_processor/config/KafkaConfig.kt`

```kotlin

```

### 5. Implement Kafka Listener
**Location**: `src/main/kotlin/com/lynceus/telemetry_processor/listener/NavigationKafkaListener.kt`

```kotlin
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
    
    @KafkaListener(
        topics = ["lynceus.prod.navigation"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun listen(message: ByteArray) {
        logger.info("Received Kafka message from topic 'lynceus.prod.navigation'")
        navigationProcessor.process(message)
    }
}
```

### 6. Error Handling Considerations
- Add `@KafkaListener` error handler for failed message processing
- Consider implementing dead letter queue for failed messages
- Add metrics and monitoring for message processing

### 7. Testing Strategy
1. Unit tests for `NavigationProcessor`
2. Integration tests with embedded Kafka
3. Test error scenarios and retry logic

## Architecture Diagram

```mermaid
graph TD
    A[Kafka Broker<br/>localhost:9092] --> B[Topic: lynceus.prod.navigation]
    B --> C[NavigationKafkaListener<br/>Spring @KafkaListener]
    C --> D[NavigationProcessor<br/>processbyte[]]
    D --> E[Logging<br/>Size & Hex Content]
    D --> F[Future: Actual Processing Logic]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#e8f5e8
    style D fill:#fff3e0
```

## Files to Create/Modify
1. `build.gradle.kts` - Update dependencies
2. `src/main/resources/application.yaml` - Add Kafka configuration
3. `src/main/kotlin/com/lynceus/telemetry_processor/processor/NavigationProcessor.kt` - New file
4. `src/main/kotlin/com/lynceus/telemetry_processor/config/KafkaConfig.kt` - New file
5. `src/main/kotlin/com/lynceus/telemetry_processor/listener/NavigationKafkaListener.kt` - New file

## Prerequisites
1. Kafka running on `localhost:9092`
2. Topic `lynceus.prod.navigation` created
3. Java 17 installed
4. Gradle build system

## Next Steps
1. Switch to Code mode to implement the changes
2. Test with local Kafka instance
3. Add monitoring and error handling as needed
4. Implement actual navigation processing logic in `NavigationProcessor`