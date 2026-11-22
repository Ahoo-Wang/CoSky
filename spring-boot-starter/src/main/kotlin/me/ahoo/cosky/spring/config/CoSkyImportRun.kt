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

package me.ahoo.cosky.spring.config

import me.ahoo.cosky.spring.CoSkyApplicationClient
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Begins a run of the imports (design §7.7). Spring loads them while it runs the environment post processors, at
 * startup and in every refresh, so this comes right before the one that loads them.
 */
internal class CoSkyImportRunBegins(private val bootstrap: ConfigurableBootstrapContext) :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        bootstrap.getOrElse(CoSkyApplicationClient::class.java, null)?.runBegins()
    }

    override fun getOrder(): Int = ConfigDataEnvironmentPostProcessor.ORDER - 1
}

/**
 * Ends a run of the imports, once every environment post processor went through: a run that fails, say on a Config
 * that does not parse, stops before, and never counts as what the imports load (#1263).
 */
internal class CoSkyImportRunEnds(private val bootstrap: ConfigurableBootstrapContext) :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        bootstrap.getOrElse(CoSkyApplicationClient::class.java, null)?.runEnds()
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
