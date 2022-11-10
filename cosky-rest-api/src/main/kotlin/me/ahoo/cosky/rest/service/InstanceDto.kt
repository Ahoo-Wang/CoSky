package me.ahoo.cosky.rest.service

import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.ServiceInstance

data class InstanceDto(
    val schema: String,
    val host: String,
    val port: Int,
    val weight: Int = 1,
    val isEphemeral: Boolean = true,
    val ttlAt: Long = -1,
    val metadata: Map<String, String> = mapOf()
) {
    fun asServiceInstance(serviceId: String): ServiceInstance {
        val instance = Instance.asInstance(serviceId, schema, host, port)
        return ServiceInstance(instance, weight, isEphemeral, ttlAt, metadata)
    }
}
