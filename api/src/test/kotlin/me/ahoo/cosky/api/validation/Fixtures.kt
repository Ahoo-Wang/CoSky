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
package me.ahoo.cosky.api.validation

import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.cosky.api.v1.rule
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.v1.subject

/**
 * Minimal valid resources of every kind; tests copy them and break one field.
 */
object Fixtures {
    const val NAMESPACE = "prod"
    const val SYSTEM = NamingRules.SYSTEM_NAMESPACE

    val config: Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "application.yaml"
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            content = "a: 1"
        }
    }

    val instance: Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "order-10.0.0.1-8080"
            lease = 7
        }
        instance = instanceSpec {
            service = "order"
            endpoint = endpoint {
                scheme = "http"
                host = "10.0.0.1"
                port = 8080
            }
            weight = 1
        }
    }

    val namespace: Resource = resource {
        meta = objectMeta {
            namespace = SYSTEM
            name = NAMESPACE
        }
        namespace = namespaceSpec { description = "production" }
    }

    val serviceAccount: Resource = resource {
        meta = objectMeta {
            namespace = SYSTEM
            name = "order-service"
        }
        serviceAccount = serviceAccountSpec { description = "order" }
    }

    val role: Resource = resource {
        meta = objectMeta {
            namespace = SYSTEM
            name = "client"
        }
        role = roleSpec {
            rules += rule {
                kinds += Kind.KIND_CONFIG
                namespaces += NAMESPACE
                verbs += Verb.VERB_GET
            }
        }
    }

    val roleBinding: Resource = resource {
        meta = objectMeta {
            namespace = SYSTEM
            name = "order-client"
        }
        roleBinding = roleBindingSpec {
            role = "client"
            subjects += subject {
                type = Subject.Type.TYPE_SERVICE_ACCOUNT
                name = "order-service"
            }
            namespaces += NAMESPACE
        }
    }

    val all: List<Resource> = listOf(config, instance, namespace, serviceAccount, role, roleBinding)

    val configKey = resourceKey {
        namespace = NAMESPACE
        kind = Kind.KIND_CONFIG
        name = "application.yaml"
    }
}

/**
 * Violations reduced to what tests assert on: the field and the code.
 */
fun List<Violation>.codes(): List<Pair<String, ViolationCode>> = map { it.field to it.code }
