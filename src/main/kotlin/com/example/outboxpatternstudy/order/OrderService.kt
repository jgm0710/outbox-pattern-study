package com.example.outboxpatternstudy.order

import com.example.outboxpatternstudy.outbox.Outbox
import com.example.outboxpatternstudy.outbox.OutboxRepository
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository,
) {

    @Transactional
    fun createOrder(command: CreateOrderCommand): UUID {
        val order: Order = command.createOrder()

        val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())


        val outbox = Outbox(
            id = order.id.toString(),
            eventType = "OrderCreated",
            payload = objectMapper.writeValueAsString(order),
            createdAt = OffsetDateTime.now(),
            processed = false
        )

        orderRepository.save(order)
        outboxRepository.save(outbox)

        return  order.id
    }
}
