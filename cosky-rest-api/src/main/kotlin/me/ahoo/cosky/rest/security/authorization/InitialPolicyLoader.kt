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

import me.ahoo.cosec.api.policy.Policy
import me.ahoo.cosec.policy.DefaultPolicyEvaluator
import me.ahoo.cosec.serialization.CoSecJsonSerializer

object InitialPolicyLoader {
    const val policyResourceName = "cosky-policy.json"
    val policy: Policy by lazy(this) {
        requireNotNull(javaClass.classLoader.getResource(policyResourceName)).let { resource ->
            resource.openStream().use {
                val policy = CoSecJsonSerializer.readValue(it, Policy::class.java)
                DefaultPolicyEvaluator.evaluate(policy)
                policy
            }
        }
    }
}
