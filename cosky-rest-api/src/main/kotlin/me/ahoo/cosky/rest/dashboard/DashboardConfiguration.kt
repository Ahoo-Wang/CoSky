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
package me.ahoo.cosky.rest.dashboard

import me.ahoo.cosky.rest.support.RequestPathPrefix
import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * for Dashboard-UI.
 *
 * @author ahoo wang
 */
@Controller
class DashboardConfiguration(private val webProperties: WebProperties, private val resourceLoader: ResourceLoader) {
    companion object {
        const val HOME_FILE = "index.html"
        const val HOME_ROUTE = "/home"
        const val CONFIG_ROUTE = "/config"
        const val SERVICE_ROUTE = "/service"
        const val NAMESPACE_ROUTE = "/namespace"
        const val USER_ROUTE = "/user"
        const val ROLE_ROUTE = "/role"
        const val AUDIT_LOG_ROUTE = "/audit-log"
        const val LOGIN_ROUTE = "/login"
    }

    private val indexResource by lazy {
        val location = webProperties.resources.staticLocations.first()
        val resourcePath = "${location.removeSuffix("/")}/$HOME_FILE"
        val resource = resourceLoader.getResource(resourcePath)
        check(resource.exists()) { "$HOME_FILE not found in $resourcePath" }
        resource
    }

    @GetMapping(
        "/",
        HOME_ROUTE,
        CONFIG_ROUTE,
        SERVICE_ROUTE,
        NAMESPACE_ROUTE,
        USER_ROUTE,
        ROLE_ROUTE,
        AUDIT_LOG_ROUTE,
        LOGIN_ROUTE,
    )
    fun home(): ResponseEntity<Resource> {
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.TEXT_HTML)
            .body(indexResource)
    }

    /**
     * compatibility routing for dashboard paths
     */
    @GetMapping(RequestPathPrefix.DASHBOARD, "${RequestPathPrefix.DASHBOARD}**")
    fun dashboard(): ResponseEntity<Void> {
        HttpStatus.PERMANENT_REDIRECT
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .location(java.net.URI.create(HOME_ROUTE))
            .build()
    }
}
