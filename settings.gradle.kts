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

rootProject.name = "CoSky"

include(":api")
include(":store")
include(":store-redis")
include(":server")
include(":client")
include(":spring-boot-starter")
include(":migration")
include(":examples:provider")
include(":examples:consumer")
include(":examples:e2e")

// The modules that support the build and the tests sit in folders by role. Their project paths, and so their artifacts,
// keep their names (design D8): `testing/test` is still `:test`, published as `cosky-test`.
mapOf(
    ":bom" to "platform/bom",
    ":dependencies" to "platform/dependencies",
    ":test" to "testing/test",
    ":conformance-test" to "testing/conformance-test",
    ":code-coverage-report" to "testing/code-coverage-report",
).forEach { (path, dir) ->
    include(path)
    project(path).projectDir = file(dir)
}
