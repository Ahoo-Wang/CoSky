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
package me.ahoo.cosky.rest.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger Config.
 *
 * @author ahoo wang
 */
@Configuration
class SwaggerConfiguration {
    val apiInfo: Info = Info()
        .title("CoSky REST API")
        .description("High-performance, low-cost microservice governance platform. Service Discovery and Configuration Service.")
        .contact(Contact().name("Ahoo Wang").url("https://github.com/Ahoo-Wang/CoSky"))
        .license(License().url("https://github.com/Ahoo-Wang/CoSky/blob/main/LICENSE").name("Apache 2.0"))
        .version("3.0.3")

    @Bean
    fun openApi(): OpenAPI = OpenAPI().apply {
        info(apiInfo)
        addSecurityItem(SecurityRequirement().addList("api_key"))
        components(Components())
        components.addSecuritySchemes(
            "api_key",
            SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .`in`(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("API Key")
                .bearerFormat("JWT"),
        )
    }
}
