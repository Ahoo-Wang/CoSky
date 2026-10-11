/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.ahoo.cosky.rest.security.authorization

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosec.api.policy.Policy
import me.ahoo.cosec.policy.DefaultPolicyEvaluator
import me.ahoo.cosec.serialization.CoSecJsonSerializer
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigWatcher
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.rest.security.authorization.InitialPolicyLoader.policyResourceName
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Mono

/**
 * The security policy: the `cosky-policy.json` config of the system namespace, or the bundled one when none is stored.
 *
 * Served from the watched config cache, so an edited policy applies as soon as the cache holds it. A failed read is not
 * cached, and a stored policy that does not parse keeps the previous policy in force.
 *
 * @author ahoo wang
 */
@Service
class CoSkyPolicy(
    private val configService: ConfigService,
    private val configWatcher: ConfigWatcher
) : InitializingBean, DisposableBean {
    companion object {
        const val policyId = policyResourceName
        private val log = KotlinLogging.logger {}
    }

    @Volatile
    private var current: Policy? = null

    @Volatile
    private var watching: Disposable? = null

    override fun afterPropertiesSet() {
        watching = configWatcher.watchConfig(Namespaced.SYSTEM, policyId)
            .subscribe { snapshot ->
                parse(snapshot.value)?.let {
                    log.info { "Policy[$policyId] is updated - version:[${snapshot.value?.version}]." }
                    current = it
                }
            }
    }

    fun getPolicy(): Mono<Policy> {
        current?.let { return Mono.just(it) }
        return configService.getConfig(Namespaced.SYSTEM, policyId)
            .mapNotNull<Policy> { parse(it) }
            .defaultIfEmpty(InitialPolicyLoader.policy)
    }

    /**
     * @return the policy of [config], the bundled policy when there is no config, null when it does not parse.
     */
    // Whatever is wrong with a stored policy, authorization must keep running on the previous one.
    @Suppress("TooGenericExceptionCaught")
    private fun parse(config: Config?): Policy? {
        if (config == null) {
            log.info { "Policy[$policyId] is not found, using initial policy." }
            return InitialPolicyLoader.policy
        }
        return try {
            CoSecJsonSerializer.readValue(config.data, Policy::class.java).also { DefaultPolicyEvaluator.evaluate(it) }
        } catch (error: Exception) {
            log.error(
                error
            ) { "Policy[$policyId] version:[${config.version}] is invalid, keeping the previous policy." }
            null
        }
    }

    override fun destroy() {
        watching?.dispose()
    }
}
