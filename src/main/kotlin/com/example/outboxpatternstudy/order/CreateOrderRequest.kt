package com.example.outboxpatternstudy.order

data class CreateOrderRequest(
    val price: Int
) {
    fun toCommand(): CreateOrderCommand {
        return CreateOrderCommand(price = price)
    }

}
