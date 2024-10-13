package com.example.outboxpatternstudy.order

import java.time.OffsetDateTime
import java.util.UUID

data class CreateOrderCommand(
    private val price: Int,
) {
    fun createOrder(): Order {
        val now = OffsetDateTime.now()

        return Order(
            id = UUID.randomUUID(),
            price = price,
            status = OrderStatus.CREATED,
            createdAt = now,
            lastModifiedAt = now
        )
    }

}
