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
