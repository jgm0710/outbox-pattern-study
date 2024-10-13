package com.example.outboxpatternstudy.order

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val orderService: OrderService,
) {


    @PostMapping("/v1/orders")
    fun createOrder(@RequestBody request: CreateOrderRequest): OrderIdResponse {
        val orderId = orderService.createOrder(request.toCommand())
        return OrderIdResponse(orderId)
    }
}
