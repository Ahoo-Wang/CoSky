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

class HostRulesTest {

    @ParameterizedTest
    @ValueSource(strings = ["0.0.0.0", "10.0.0.1", "192.168.1.255", "255.255.255.255"])
    fun `IPv4 accepts dotted quads`(value: String) {
        HostRules.isIpv4(value).assert().isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "1.2.3", "1.2.3.4.5", "256.0.0.1", "1.2.3.-1", "01.2.3.4", "1.2.3.00", "1..3.4",
            "a.b.c.d", "1.2.3.4 ", "9999.1.1.1"
        ],
    )
    fun `IPv4 rejects everything else`(value: String) {
        HostRules.isIpv4(value).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "::", "::1", "1::", "fe80::1", "2001:db8:0:0:0:0:2:1", "2001:DB8::2:1", "1:2:3:4:5:6:7::",
            "::1:2:3:4:5:6:7", "::ffff:192.0.2.1", "64:ff9b::192.0.2.33", "1:2:3:4:5:6:1.2.3.4"
        ],
    )
    fun `IPv6 accepts RFC 4291 text forms`(value: String) {
        HostRules.isIpv6(value).assert().isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "1", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1::2::3", ":::", ":1::", "1::2:", "12345::",
            "g::1", "[::1]", "fe80::1%eth0", "1:2:3:4:5:6:7:1.2.3.4", "::1.2.3.4:1", "1.2.3.4::", "::256.1.1.1",
            "1::2:3:4:5:6:7:8"
        ],
    )
    fun `IPv6 rejects everything else`(value: String) {
        HostRules.isIpv6(value).assert().isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "localhost", "my-host.example.com", "1a.example", "a1", "example.c0m"])
    fun `hostnames follow RFC 1123`(value: String) {
        HostRules.isHostname(value).assert().isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", ".", "a.", ".a", "-a", "a-", "a_b", "a..b", "1.2.3.4", "host.123", "é.com"])
    fun `hostnames reject everything else`(value: String) {
        HostRules.isHostname(value).assert().isFalse()
    }

    @Test
    fun `hostname labels are at most 63 and names at most 253 characters`() {
        HostRules.isHostname("a".repeat(63) + ".com").assert().isTrue()
        HostRules.isHostname("a".repeat(64) + ".com").assert().isFalse()
        val name253 = List(4) { "a".repeat(62) }.joinToString(".") + ".b"
        name253.length.assert().isEqualTo(253)
        HostRules.isHostname(name253).assert().isTrue()
        HostRules.isHostname(name253 + "b").assert().isFalse()
    }

    @Test
    fun `host accepts any of the three forms`() {
        HostRules.isHost("example.com").assert().isTrue()
        HostRules.isHost("10.0.0.1").assert().isTrue()
        HostRules.isHost("::1").assert().isTrue()
        HostRules.isHost("[::1]").assert().isFalse()
    }
}
