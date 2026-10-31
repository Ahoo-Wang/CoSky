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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NamingRulesTest {

    @ParameterizedTest
    @ValueSource(strings = ["a", "0", "default", "prod-1", "a-b-c", "1a", "a1"])
    fun `namespace accepts DNS labels`(value: String) {
        NamingRules.isNamespace(value).assert().isTrue()
    }

    @Test
    fun `namespace accepts exactly 63 characters`() {
        NamingRules.isNamespace("a".repeat(63)).assert().isTrue()
        NamingRules.isNamespace("a".repeat(64)).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "-a", "a-", "A", "prod_1", "a.b", "_system", "cosky-{default}", "a:b", "a b", "a\n", "ä",
        ],
    )
    fun `namespace rejects everything else`(value: String) {
        NamingRules.isNamespace(value).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "order", "order-service", "order.service", "a.b-c", "1"])
    fun `service accepts DNS names`(value: String) {
        NamingRules.isService(value).assert().isTrue()
    }

    @Test
    fun `service accepts exactly 63 characters`() {
        NamingRules.isService("a".repeat(63)).assert().isTrue()
        NamingRules.isService("a".repeat(64)).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", ".a", "a.", "-a", "a-", "Order", "order_service", "a/b", "a\n"])
    fun `service rejects everything else`(value: String) {
        NamingRules.isService(value).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "application.yaml", "shared/datasource.yaml", "A_B-c.d/e", "."])
    fun `config name allows path-style names`(value: String) {
        NamingRules.CONFIG_NAME.matches(value).assert().isTrue()
    }

    @Test
    fun `config name accepts exactly 253 characters`() {
        NamingRules.CONFIG_NAME.matches("a".repeat(253)).assert().isTrue()
        NamingRules.CONFIG_NAME.matches("a".repeat(254)).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "a b", "a:b", "a{b}", "a\\b", "a\n", "配置"])
    fun `config name rejects other characters`(value: String) {
        NamingRules.CONFIG_NAME.matches(value).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "order-10.0.0.1-8080", "Order_1.x"])
    fun `instance name accepts its characters`(value: String) {
        NamingRules.INSTANCE_NAME.matches(value).assert().isTrue()
    }

    @Test
    fun `instance name accepts exactly 253 characters`() {
        NamingRules.INSTANCE_NAME.matches("a".repeat(253)).assert().isTrue()
        NamingRules.INSTANCE_NAME.matches("a".repeat(254)).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "a/b", "a:b", "a b", "a\n"])
    fun `instance name rejects other characters`(value: String) {
        NamingRules.INSTANCE_NAME.matches(value).assert().isFalse()
    }

    @Test
    fun `config name segments must not be empty, dot or dot-dot`() {
        NamingRules.isConfigName("shared/datasource.yaml").assert().isTrue()
        NamingRules.isConfigName(".").assert().isFalse()
        NamingRules.isConfigName("a//b").assert().isFalse()
        NamingRules.isConfigName("a b").assert().isFalse()
    }

    @Test
    fun `instance name excludes dot and dot-dot`() {
        NamingRules.isInstanceName("order-1").assert().isTrue()
        NamingRules.isInstanceName("..").assert().isFalse()
        NamingRules.isInstanceName("a/b").assert().isFalse()
    }

    @Test
    fun `system namespace never matches the namespace rule`() {
        NamingRules.isNamespace(NamingRules.SYSTEM_NAMESPACE).assert().isFalse()
    }

    @Test
    fun `default instance name joins service host and port`() {
        NamingRules.defaultInstanceName("order", "10.0.0.1", 8080).assert().isEqualTo("order-10.0.0.1-8080")
    }

    @Test
    fun `default instance name replaces illegal characters`() {
        NamingRules.defaultInstanceName("order", "fe80::1%eth0", 8080).assert().isEqualTo("order-fe80--1-eth0-8080")
    }

    @Test
    fun `default instance name is cut to the maximum length`() {
        val name = NamingRules.defaultInstanceName("order", "h".repeat(300), 8080)
        name.length.assert().isEqualTo(NamingRules.MAX_INSTANCE_NAME_LENGTH)
        NamingRules.INSTANCE_NAME.matches(name).assert().isTrue()
    }
}
