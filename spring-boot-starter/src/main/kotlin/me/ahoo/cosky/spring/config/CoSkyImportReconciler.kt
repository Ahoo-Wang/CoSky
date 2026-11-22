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

package me.ahoo.cosky.spring.config

import me.ahoo.cosky.spring.CoSkyApplicationClient
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.SmartApplicationListener
import org.springframework.core.Ordered

/**
 * Keeps the Environment to what the imports load (design §7.7): once a refresh has applied what its run of the imports
 * loaded, the Configs that run did not load leave it. Counting starts once the application has started. A refresh that
 * fails publishes nothing, and leaves the Environment as it was; an [EnvironmentChangeEvent] of something else after
 * it, say of a property set through the `env` endpoint, finds its run not gone through, and leaves it as well.
 *
 * On the [EnvironmentChangeEvent] a refresh publishes once the Environment is up to date, which comes before its
 * refresh scope is cleared, and also where the application only calls `ContextRefresher.refreshEnvironment`. First
 * among its listeners, so that the others see the Environment as it is now. What left is announced as another
 * [EnvironmentChangeEvent], since the refresh did not see it go, so that `@ConfigurationProperties` beans are bound
 * again without it.
 */
internal class CoSkyImportReconciler(
    private val application: CoSkyApplicationClient,
    private val context: ConfigurableApplicationContext,
) : SmartApplicationListener {
    override fun supportsEventType(eventType: Class<out ApplicationEvent>): Boolean =
        ApplicationReadyEvent::class.java.isAssignableFrom(eventType) ||
            EnvironmentChangeEvent::class.java.isAssignableFrom(eventType)

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationReadyEvent -> application.started()
            is EnvironmentChangeEvent -> {
                val left = application.reconcile(context.environment)
                if (left.isNotEmpty()) {
                    context.publishEvent(EnvironmentChangeEvent(context, left))
                }
            }
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
