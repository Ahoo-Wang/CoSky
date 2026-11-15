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
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.SmartApplicationListener
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Keeps the Environment to what the imports load (design §7.7): after every refresh that succeeded, the Configs no
 * import loaded leave it. Counting starts once the application has started. A refresh that fails publishes nothing, and
 * leaves the Environment as it was.
 */
internal class CoSkyImportReconciler(
    private val application: CoSkyApplicationClient,
    private val environment: ConfigurableEnvironment,
) : SmartApplicationListener {
    override fun supportsEventType(eventType: Class<out ApplicationEvent>): Boolean =
        ApplicationReadyEvent::class.java.isAssignableFrom(eventType) ||
            RefreshScopeRefreshedEvent::class.java.isAssignableFrom(eventType)

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationReadyEvent -> application.started()
            is RefreshScopeRefreshedEvent -> application.reconcile(environment)
        }
    }
}
