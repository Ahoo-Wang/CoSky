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

import io.swagger.v3.oas.annotations.tags.Tag
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigHistory
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigVersion
import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.rest.support.RequestPathPrefix
import me.ahoo.cosky.rest.util.Zips.ZipItem
import me.ahoo.cosky.rest.util.Zips.ZipItem.Companion.of
import me.ahoo.cosky.rest.util.Zips.unzip
import me.ahoo.cosky.rest.util.Zips.zip
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Config Controller.
 *
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.CONFIGS_PREFIX)
@Tag(name = "Config")
class ConfigController(private val configService: ConfigService) {

    companion object {
        private val log = LoggerFactory.getLogger(ConfigController::class.java)
        const val IMPORT_SUPPORT_EXT = "zip"
        const val IMPORT_POLICY_SKIP = "skip"
        const val IMPORT_POLICY_OVERWRITE = "overwrite"
        const val NACOS_DEFAULT_GROUP = "DEFAULT_GROUP/"
        const val MAX_IMPORT_ZIP_SIZE = 10 * 1024 * 1024
        const val MAX_UNZIPPED_SIZE = 64L * 1024 * 1024
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
        val importPolicy = if (policy.isNullOrEmpty()) IMPORT_POLICY_SKIP else policy
        if (importPolicy != IMPORT_POLICY_SKIP && importPolicy != IMPORT_POLICY_OVERWRITE) {
            return Mono.error(IllegalArgumentException("Unexpected policy[skip,overwrite] value: $importPolicy"))
        }
        return importZip
            .switchIfEmpty(Mono.error(IllegalArgumentException("importZip can not be empty!")))
            .flatMap { filePart ->
                val importFileExt = filePart.filename().substringAfterLast('.', "").lowercase()
                require(IMPORT_SUPPORT_EXT == importFileExt) { "Illegal file type:[$importFileExt],expect:[zip]!" }
                DataBufferUtils.join(filePart.content(), MAX_IMPORT_ZIP_SIZE)
                    .onErrorMap(DataBufferLimitException::class.java) {
                        IllegalArgumentException("importZip exceeds the limit of [$MAX_IMPORT_ZIP_SIZE] bytes.", it)
                    }
            }
            .map { dataBuffer ->
                try {
                    dataBuffer.asInputStream().use { unzip(it, MAX_UNZIPPED_SIZE) }
                } finally {
                    DataBufferUtils.release(dataBuffer)
                }
            }
            .flatMap { zipItems ->
                Flux.fromIterable(zipItems)
                    .flatMap { importItem(namespace, importPolicy, it) }
                    .filter { it }
                    .count()
                    .map { succeeded -> ImportResponse(total = zipItems.size, succeeded = succeeded.toInt()) }
            }
    }

    private fun importItem(namespace: String, importPolicy: String, zipItem: ZipItem): Mono<Boolean> {
        val configId = zipItem.name.removePrefix(NACOS_DEFAULT_GROUP).replace('/', '-')
        if (importPolicy == IMPORT_POLICY_OVERWRITE) {
            return configService.setConfig(namespace, configId, zipItem.data)
        }
        return configService.containsConfig(namespace, configId)
            .flatMap { contained ->
                if (contained) {
                    log.info("ImportZip - Skip - [{}]@[{}] has contained.", configId, namespace)
                    return@flatMap Mono.just(false)
                }
                configService.setConfig(namespace, configId, zipItem.data)
            }
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
