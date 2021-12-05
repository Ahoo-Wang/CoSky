/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.config.Config;
import me.ahoo.cosky.config.ConfigHistory;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.ConfigVersion;
import me.ahoo.cosky.core.CoSky;
import me.ahoo.cosky.core.CoskyException;
import me.ahoo.cosky.rest.dto.config.ImportResponse;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.rest.util.Zips;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;


/**
 * @author ahoo wang
 */
@CrossOrigin("*")
@RestController
@RequestMapping(RequestPathPrefix.CONFIGS_PREFIX)
@Slf4j
public class ConfigController {

    public static final String IMPORT_SUPPORT_EXT = "zip";
    public static final String IMPORT_POLICY_SKIP = "skip";
    public static final String IMPORT_POLICY_OVERWRITE = "overwrite";
    public static final String NACOS_DEFAULT_GROUP = "DEFAULT_GROUP/";
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Mono<Set<String>> getConfigs(@PathVariable String namespace) {
        return configService.getConfigs(namespace);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ImportResponse> importZip(@PathVariable String namespace, @RequestParam(required = false) String policy, @RequestPart Mono<FilePart> importZip) {
        if (Strings.isNullOrEmpty(policy)) {
            policy = IMPORT_POLICY_SKIP;
        }
        final String importPolicy = policy;
        ImportResponse importResponse = new ImportResponse();
        return importZip
                .switchIfEmpty(Mono.error(new CoskyException("importZip can not be empty!")))
                .doOnNext(filePart -> {
                    String importFileExt = Files.getFileExtension(filePart.filename()).toLowerCase();
                    Preconditions.checkArgument(IMPORT_SUPPORT_EXT.equals(importFileExt), Strings.lenientFormat("Illegal file type:[%s],expect:[zip]!", importFileExt));
                })
                .flatMapMany(filePart -> filePart
                        .content()
                        .flatMapIterable(dataBuffer -> {
                            List<Zips.ZipItem> zipItems = Zips.unzip(dataBuffer.asInputStream());
                            importResponse.setTotal(zipItems.size());
                            return zipItems;
                        })
                )
                .flatMap(zipItem -> {
                    String zipItemName = zipItem.getName();
                    if (zipItemName.startsWith(NACOS_DEFAULT_GROUP)) {
                        zipItemName = zipItemName.substring(NACOS_DEFAULT_GROUP.length());
                    }
                    if (zipItemName.contains("/")) {
                        zipItemName = zipItemName.replaceAll("/", "-");
                    }
                    final String configId = zipItemName;
                    final String configData = zipItem.getData();
                    switch (importPolicy) {
                        case IMPORT_POLICY_OVERWRITE: {
                            return configService.setConfig(namespace, configId, configData);
                        }
                        case IMPORT_POLICY_SKIP: {
                            return configService.containsConfig(namespace, configId)
                                    .filter(contained -> {
                                        if (contained) {
                                            if (log.isInfoEnabled()) {
                                                log.info("importZip - Skip - [{}]@[{}] has contained.", configId, namespace);
                                            }
                                        }
                                        return !contained;
                                    })
                                    .flatMap(contained -> configService.setConfig(namespace, configId, configData));
                        }
                        default:
                            return reactor.core.publisher.Flux.error(new IllegalStateException("Unexpected policy[skip,overwrite] value: " + importPolicy));
                    }
                })
                .map(result -> result ? 1 : 0)
                .reduce(Integer::sum)
                .map(succeeded -> {
                    importResponse.setSucceeded(succeeded);
                    return importResponse;
                })
                .defaultIfEmpty(new ImportResponse());
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_EXPORT)
    public Mono<ResponseEntity<byte[]>> exportZip(@PathVariable String namespace) {
        return configService.getConfigs(namespace)
                .flatMapIterable(configs -> configs)
                .flatMap(cfg -> configService.getConfig(namespace, cfg))
                .map(config -> Zips.ZipItem.of(config.getConfigId(), config.getData()))
                .collectList()
                .map(zipItems -> {
                    HttpHeaders headers = new HttpHeaders();
                    String fileName = CoSky.COSKY + "_export_config_" + System.currentTimeMillis() / 1000 + ".zip";
                    headers.add("Content-Disposition", "attachment;filename=" + fileName);
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    return new ResponseEntity<>(Zips.zip(zipItems), headers, HttpStatus.OK);
                });
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Mono<Boolean> setConfig(@PathVariable String namespace, @PathVariable String configId, @RequestBody String data) {
        return configService.setConfig(namespace, configId, data);
    }

    @DeleteMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Mono<Boolean> removeConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.removeConfig(namespace, configId);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Mono<Config> getConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfig(namespace, configId);
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG_TO)
    public Mono<Boolean> rollback(@PathVariable String namespace, @PathVariable String configId, @PathVariable int targetVersion) {
        return configService.rollback(namespace, configId, targetVersion);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS)
    public Mono<List<ConfigVersion>> getConfigVersions(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfigVersions(namespace, configId);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS_VERSION)
    public Mono<ConfigHistory> getConfigHistory(@PathVariable String namespace, @PathVariable String configId, @PathVariable int version) {
        return configService.getConfigHistory(namespace, configId, version);
    }

}
