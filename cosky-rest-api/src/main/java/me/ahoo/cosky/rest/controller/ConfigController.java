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
import lombok.var;
import me.ahoo.cosky.config.Config;
import me.ahoo.cosky.config.ConfigHistory;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.config.ConfigVersion;
import me.ahoo.cosky.core.CoSky;
import me.ahoo.cosky.rest.dto.config.ImportResponse;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.rest.util.Zips;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<Set<String>> getConfigs(@PathVariable String namespace) {
        return configService.getConfigs(namespace);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ImportResponse> importZip(@PathVariable String namespace, @RequestParam String policy, @RequestPart MultipartFile importZip) throws IOException {
        var importResponse = new ImportResponse();
        if (Objects.isNull(importZip) || importZip.isEmpty()) {
            return CompletableFuture.completedFuture(importResponse);
        }

        var importFileExt = Files.getFileExtension(importZip.getOriginalFilename()).toLowerCase();
        Preconditions.checkArgument(IMPORT_SUPPORT_EXT.equals(importFileExt), Strings.lenientFormat("Illegal file type:[%s],expect:[zip]!", importFileExt));

        var zipItems = Zips.unzip(importZip.getBytes());
        importResponse.setTotal(zipItems.size());
        List<CompletableFuture<Boolean>> importFutures = new ArrayList<>(zipItems.size());
        for (Zips.ZipItem zipItem : zipItems) {
            String zipItemName = zipItem.getName();
            if (zipItemName.startsWith(NACOS_DEFAULT_GROUP)) {
                zipItemName = zipItemName.substring(NACOS_DEFAULT_GROUP.length());
            }
            if (zipItemName.contains("/")) {
                zipItemName = zipItemName.replaceAll("/", "-");
            }
            final String configId = zipItemName;
            final String configData = zipItem.getData();
            CompletableFuture<Boolean> setFuture;
            switch (policy) {
                case IMPORT_POLICY_OVERWRITE: {
                    setFuture = configService.setConfig(namespace, configId, configData);
                    break;
                }
                case IMPORT_POLICY_SKIP: {
                    setFuture = configService.containsConfig(namespace, configId).thenCompose(contained -> {
                        if (contained) {
                            if (log.isInfoEnabled()) {
                                log.info("importZip - Skip - [{}]@[{}] has contained.", configId, namespace);
                            }
                            return CompletableFuture.completedFuture(false);
                        }
                        return configService.setConfig(namespace, configId, configData);
                    });
                    break;
                }
                default:
                    throw new IllegalStateException("Unexpected policy[skip,overwrite] value: " + policy);
            }
            importFutures.add(setFuture);
        }

        if (!importFutures.isEmpty()) {
            return CompletableFuture.allOf(importFutures.toArray(new CompletableFuture[importFutures.size()])).thenApply((nil) ->
                    {
                        int succeeded = (int) importFutures.stream().filter(future -> future.join()).count();
                        importResponse.setSucceeded(succeeded);
                        return importResponse;
                    }
            );
        }
        return CompletableFuture.completedFuture(importResponse);

    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_EXPORT)
    public CompletableFuture<ResponseEntity<byte[]>> exportZip(@PathVariable String namespace) {

        return configService.getConfigs(namespace).thenCompose(configs -> {
            List<Zips.ZipItem> zipItems = new ArrayList<>(configs.size());

            var getConfigFutures = configs.stream().map(cfg -> configService.getConfig(namespace, cfg)
                    .thenAccept(config -> zipItems.add(Zips.ZipItem.of(config.getConfigId(), config.getData()))))
                    .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(getConfigFutures).thenApply(nil -> {
                HttpHeaders headers = new HttpHeaders();
                String fileName = CoSky.COSKY + "_export_config_" + System.currentTimeMillis() / 1000 + ".zip";
                headers.add("Content-Disposition", "attachment;filename=" + fileName);
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                return new ResponseEntity<>(Zips.zip(zipItems), headers, HttpStatus.OK);
            });
        });
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public CompletableFuture<Boolean> setConfig(@PathVariable String namespace, @PathVariable String configId, @RequestBody String data) {
        return configService.setConfig(namespace, configId, data);
    }

    @DeleteMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public CompletableFuture<Boolean> removeConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.removeConfig(namespace, configId);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public CompletableFuture<Config> getConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfig(namespace, configId);
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG_TO)
    public CompletableFuture<Boolean> rollback(@PathVariable String namespace, @PathVariable String configId, @PathVariable int targetVersion) {
        return configService.rollback(namespace, configId, targetVersion);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS)
    public CompletableFuture<List<ConfigVersion>> getConfigVersions(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfigVersions(namespace, configId);
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS_VERSION)
    public CompletableFuture<ConfigHistory> getConfigHistory(@PathVariable String namespace, @PathVariable String configId, @PathVariable int version) {
        return configService.getConfigHistory(namespace, configId, version);
    }

}
