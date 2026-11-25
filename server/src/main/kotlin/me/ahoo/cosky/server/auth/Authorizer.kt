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

package me.ahoo.cosky.server.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.RoleBindingSpec
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Verb

/**
 * What a rule grants on the objects of one kind and namespace (design §7.8).
 */
sealed interface Permission {
    /**
     * @param name the name of a Config or other object, the service of an Instance; see [authorizedName].
     */
    fun allows(name: String): Boolean

    data object None : Permission {
        override fun allows(name: String): Boolean = false
    }

    data object All : Permission {
        override fun allows(name: String): Boolean = true
    }

    /**
     * @param patterns exact values, or prefixes when they end in `*`.
     */
    data class Names(val patterns: Set<String>) : Permission {
        override fun allows(name: String): Boolean = patterns.any { pattern ->
            if (pattern.endsWith(WILDCARD)) name.startsWith(pattern.dropLast(1)) else name == pattern
        }
    }

    companion object {
        const val WILDCARD = "*"
    }
}

/**
 * What [Permission.allows] compares: an Instance is authorized by its service, or an account could register as another
 * service (design §7.8).
 */
val Resource.authorizedName: String
    get() = if (hasInstance()) instance.service else meta.name

/**
 * What a write gives someone, which whoever writes it must hold already (design §7.8).
 */
sealed interface Grant {
    /** A Role: each of its rules, in every namespace the rule names. */
    data class OfRole(val rules: List<Rule>) : Grant

    /** A RoleBinding: what its role grants where the binding and each rule meet. */
    data class OfBinding(val binding: RoleBindingSpec) : Grant

    /** A token of a ServiceAccount: whatever the account may do. */
    data class OfAccount(val account: String) : Grant

    companion object {
        /**
         * What writing [resource] grants; null for a kind that grants nothing.
         */
        fun of(resource: Resource): Grant? = when {
            resource.hasRole() -> OfRole(resource.role.rulesList)
            resource.hasRoleBinding() -> OfBinding(resource.roleBinding)
            else -> null
        }
    }
}

interface Authorizer {
    fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission

    /**
     * Refuses [grant] unless [principal] holds all of it already, by the rules as of the store's latest write: nobody
     * gives more than they have, and a replica that is behind cannot be used to (design §7.8).
     *
     * @throws me.ahoo.cosky.api.error.CoSkyException with `PERMISSION_DENIED`, saying what [principal] does not hold.
     */
    suspend fun requireHeld(principal: Principal, grant: Grant)

    /**
     * Whether the credentials of [principal] still hold, for the streams it opened earlier.
     */
    fun isValid(principal: Principal): Boolean

    /**
     * Emits when any permission may have changed, so that long-lived watches can be evaluated again.
     */
    val changes: Flow<Unit>

    companion object {
        /**
         * Where authentication is off.
         */
        val ALLOW_ALL: Authorizer = object : Authorizer {
            override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
                Permission.All

            override suspend fun requireHeld(principal: Principal, grant: Grant) = Unit

            override fun isValid(principal: Principal): Boolean = true

            override val changes: Flow<Unit> = emptyFlow()
        }
    }
}
