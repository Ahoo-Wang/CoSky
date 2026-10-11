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
package me.ahoo.cosky.config

/**
 * ConfigData .
 *
 * @author ahoo wang
 */
object TestData {
    const val NAMESPACE = "ben_cfg"
    const val DATA = """
        spring:
          cloud:
            cosky:
              discovery:
                registry:
                  weight: 8
          web:
            resources:
              static-locations: file:./cosky-dashboard/dist/dashboard/
          webflux:
            static-path-pattern: /dashboard/**
        cosky:
          security:
            enabled: true
            audit-log:
              action: write
        cosid:
          namespace: cosky
          machine:
            enabled: true
            distributor:
              type: redis
          snowflake:
            enabled: true
            share:
              converter:
                type: radix
    """
}
