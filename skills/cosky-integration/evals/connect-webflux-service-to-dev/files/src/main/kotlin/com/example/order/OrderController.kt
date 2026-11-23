package com.example.order

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(@Value("\${order.max-items}") private val maxItems: Int) {
    @GetMapping("/orders/{id}")
    suspend fun order(@PathVariable id: String): Map<String, Any> = mapOf("id" to id, "maxItems" to maxItems)
    // TODO: check stock by calling payment-service's GET /payments/{orderId}
}
