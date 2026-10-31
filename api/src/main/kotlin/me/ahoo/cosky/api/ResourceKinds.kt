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
package me.ahoo.cosky.api

import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource

/**
 * The kind of a resource, determined by which spec is set; [Kind.KIND_UNSPECIFIED] when none is.
 */
val Resource.kind: Kind
    get() = when (specCase) {
        Resource.SpecCase.NAMESPACE -> Kind.KIND_NAMESPACE
        Resource.SpecCase.CONFIG -> Kind.KIND_CONFIG
        Resource.SpecCase.INSTANCE -> Kind.KIND_INSTANCE
        Resource.SpecCase.SERVICE_ACCOUNT -> Kind.KIND_SERVICE_ACCOUNT
        Resource.SpecCase.ROLE -> Kind.KIND_ROLE
        Resource.SpecCase.ROLE_BINDING -> Kind.KIND_ROLE_BINDING
        Resource.SpecCase.SPEC_NOT_SET, null -> Kind.KIND_UNSPECIFIED
    }

/**
 * Global kinds live in the `_system` namespace; the others belong to a user namespace.
 */
val Kind.isGlobal: Boolean
    get() = this == Kind.KIND_NAMESPACE ||
        this == Kind.KIND_SERVICE_ACCOUNT ||
        this == Kind.KIND_ROLE ||
        this == Kind.KIND_ROLE_BINDING
