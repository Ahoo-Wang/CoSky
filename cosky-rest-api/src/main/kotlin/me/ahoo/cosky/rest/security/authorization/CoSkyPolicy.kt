package me.ahoo.cosky.rest.security.authorization

import me.ahoo.cosec.api.policy.Policy
import me.ahoo.cosec.serialization.CoSecJsonSerializer
import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.rest.security.authorization.InitialPolicyLoader.policyResourceName
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono

@Service
class CoSkyPolicy(
    private val configService: ConfigService,
    private val configEventListenerContainer: ConfigEventListenerContainer
) : InitializingBean {
    companion object {
        const val policyId = policyResourceName
        val namespacedPolicyId = me.ahoo.cosky.config.NamespacedConfigId(Namespaced.SYSTEM, policyId)
        private val log = LoggerFactory.getLogger(CoSkyPolicy::class.java)
    }

    private var policyCache: Mono<Policy> = getPolicyCache()

    override fun afterPropertiesSet() {
        configEventListenerContainer.receive(namespacedPolicyId)
            .subscribe {
                if (log.isInfoEnabled) {
                    log.info("Policy[{}] is updated - ${it.event}.", policyId)
                }
                policyCache = getPolicyCache()
            }
    }

    private fun getPolicyCache(): Mono<Policy> {
        return configService.getConfig(Namespaced.SYSTEM, policyId)
            .map { config ->
                CoSecJsonSerializer.readValue(config.data, Policy::class.java)
            }.switchIfEmpty {
                if (log.isInfoEnabled) {
                    log.info("Policy[{}] is not found, using initial policy.", policyId)
                }
                InitialPolicyLoader.policy.toMono()
            }.cache()
    }

    fun getPolicy(): Mono<Policy> {
        return policyCache
    }
}
