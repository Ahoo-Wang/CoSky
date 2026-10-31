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

package me.ahoo.cosky.spring

import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.ConnectionState
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * The state of the connection to the server. Degraded is still up: the application keeps working from its local view,
 * and restarting it would not help (design §9).
 */
class CoSkyHealthIndicator(private val client: CoSkyClient) : HealthIndicator {
    override fun health(): Health {
        val state = client.connectionState.value
        val health = if (state == ConnectionState.CLOSED) Health.down() else Health.up()
        return health.withDetail("connection", state.name).build()
    }
}
