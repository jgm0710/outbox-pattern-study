package com.example.outboxpatternstudy.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaDomainEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(KafkaDomainEventPublisher::class.java)

    fun publishEvent(topic: String, key: String, payload: String) {
        try {
            // JSON 문자열을 JsonNode로 변환
            val jsonNode = objectMapper.readTree(payload)
            
            logger.info("Publishing event to topic: $topic with key: $key")
            
            // Kafka로 이벤트 발행
            kafkaTemplate.send(topic, key, jsonNode).whenComplete { result, ex ->
                if (ex == null) {
                    logger.info("Event published successfully to topic: ${result.recordMetadata.topic()}, " +
                            "partition: ${result.recordMetadata.partition()}, " +
                            "offset: ${result.recordMetadata.offset()}")
                } else {
                    logger.error("Failed to publish event to topic: $topic", ex)
                }
            }
        } catch (e: Exception) {
            logger.error("Error publishing event to topic: $topic", e)
            throw e
        }
    }
}
