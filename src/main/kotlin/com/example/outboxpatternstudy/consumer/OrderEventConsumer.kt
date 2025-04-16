package com.example.outboxpatternstudy.consumer

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer {
    private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    @KafkaListener(topics = ["order-events"], groupId = "orders")
    fun consume(message: String) {
        logger.info("Received message: $message")
        // 이벤트 처리 로직
    }
}
