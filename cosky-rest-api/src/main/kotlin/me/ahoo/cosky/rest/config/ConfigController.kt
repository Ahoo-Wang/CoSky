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
package me.ahoo.cosky.rest.config

import com.google.common.io.Files
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigHistory
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigVersion
import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.rest.support.RequestPathPrefix
import me.ahoo.cosky.rest.util.Zips.ZipItem.Companion.of
import me.ahoo.cosky.rest.util.Zips.unzip
import me.ahoo.cosky.rest.util.Zips.zip
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.util.*

/**
 * Config Controller.
 *
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.CONFIGS_PREFIX)
class ConfigController(private val configService: ConfigService) {

    companion object {
        private val log = LoggerFactory.getLogger(ConfigController::class.java)
        const val IMPORT_SUPPORT_EXT = "zip"
        const val IMPORT_POLICY_SKIP = "skip"
        const val IMPORT_POLICY_OVERWRITE = "overwrite"
        const val NACOS_DEFAULT_GROUP = "DEFAULT_GROUP/"
    }

    @GetMapping
    fun getConfigs(@PathVariable namespace: String): Mono<List<String>> {
        return configService.getConfigs(namespace).collectList()
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importZip(
        @PathVariable namespace: String,
        @RequestParam(required = false) policy: String?,
        @RequestPart importZip: Mono<FilePart>
    ): Mono<ImportResponse> {
        val importPolicy = if (policy.isNullOrEmpty()) {
            IMPORT_POLICY_SKIP
        } else {
            policy
        }

        val importResponse = ImportResponse()
        return importZip
            .switchIfEmpty(Mono.error(IllegalArgumentException("importZip can not be empty!")))
            .doOnNext {
                val importFileExt = Files.getFileExtension(it.filename()).lowercase(Locale.getDefault())
                require(IMPORT_SUPPORT_EXT == importFileExt) { "Illegal file type:[$importFileExt],expect:[zip]!" }
            }
            .flatMapMany { filePart ->
                filePart
                    .content()
                    .flatMapIterable { dataBuffer ->
                        val zipItems = unzip(dataBuffer.asInputStream())
                        importResponse.total = zipItems.size
                        zipItems
                    }
            }
            .flatMap { zipItem ->
                var zipItemName = zipItem.name
                if (zipItemName.startsWith(NACOS_DEFAULT_GROUP)) {
                    zipItemName = zipItemName.substring(NACOS_DEFAULT_GROUP.length)
                }
                if (zipItemName.contains("/")) {
                    zipItemName = zipItemName.replace("/".toRegex(), "-")
                }
                val configId = zipItemName
                val configData = zipItem.data
                when (importPolicy) {
                    IMPORT_POLICY_OVERWRITE -> {
                        return@flatMap configService.setConfig(namespace, configId, configData)
                    }

                    IMPORT_POLICY_SKIP -> {
                        return@flatMap configService.containsConfig(namespace, configId)
                            .filter { contained ->
                                if (contained) {
                                    if (log.isInfoEnabled) {
                                        log.info("ImportZip - Skip - [{}]@[{}] has contained.", configId, namespace)
                                    }
                                }
                                !contained
                            }
                            .flatMap {
                                configService.setConfig(
                                    namespace,
                                    configId,
                                    configData,
                                )
                            }
                    }

                    else -> return@flatMap IllegalStateException(
                        "Unexpected policy[skip,overwrite] value: $importPolicy"
                    ).toFlux()
                }
            }
            .map { result -> if (result) 1 else 0 }
            .reduce { a: Int, b: Int ->
                Integer.sum(
                    a,
                    b,
                )
            }
            .map { succeeded ->
                importResponse.succeeded = succeeded
                importResponse
            }
            .defaultIfEmpty(ImportResponse())
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_EXPORT)
    fun exportZip(@PathVariable namespace: String): Mono<ResponseEntity<ByteArray>> {
        return configService.getConfigs(namespace)
            .flatMap {
                configService.getConfig(namespace, it)
            }
            .map { of(it.configId, it.data) }
            .collectList()
            .map {
                val headers = HttpHeaders()
                val fileName = "${CoSky.COSKY}_${namespace}_config_${System.currentTimeMillis()}.zip"
                headers.add("Content-Disposition", "attachment;filename=$fileName")
                headers.contentType = MediaType.APPLICATION_OCTET_STREAM
                ResponseEntity(zip(it), headers, HttpStatus.OK)
            }
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG)
    fun setConfig(
        @PathVariable namespace: String,
        @PathVariable configId: String,
        @RequestBody data: String
    ): Mono<Boolean> {
        return configService.setConfig(namespace, configId, data)
    }

    @DeleteMapping(RequestPathPrefix.CONFIGS_CONFIG)
    fun removeConfig(@PathVariable namespace: String, @PathVariable configId: String): Mono<Boolean> {
        return configService.removeConfig(namespace, configId)
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG)
    fun getConfig(@PathVariable namespace: String, @PathVariable configId: String): Mono<Config> {
        return configService.getConfig(namespace, configId)
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG_TO)
    fun rollback(
        @PathVariable namespace: String,
        @PathVariable configId: String,
        @PathVariable targetVersion: Int
    ): Mono<Boolean> {
        return configService.rollback(namespace, configId, targetVersion)
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS)
    fun getConfigVersions(
        @PathVariable namespace: String,
        @PathVariable configId: String
    ): Mono<List<ConfigVersion>> {
        return configService.getConfigVersions(namespace, configId).collectList()
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS_VERSION)
    fun getConfigHistory(
        @PathVariable namespace: String,
        @PathVariable configId: String,
        @PathVariable version: Int
    ): Mono<ConfigHistory> {
        return configService.getConfigHistory(namespace, configId, version)
    }
}
