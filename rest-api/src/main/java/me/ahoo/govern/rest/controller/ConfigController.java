package me.ahoo.govern.rest.controller;

import me.ahoo.govern.config.Config;
import me.ahoo.govern.config.ConfigHistory;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.config.ConfigVersion;
import me.ahoo.govern.rest.support.RequestPathPrefix;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping(RequestPathPrefix.CONFIGS_PREFIX)
public class ConfigController {
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Set<String> getConfigs(@PathVariable String namespace) {
        return configService.getConfigs(namespace).join();
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Boolean setConfig(@PathVariable String namespace, @PathVariable String configId, @RequestBody String data) {
        return configService.setConfig(namespace, configId, data).join();
    }

    @DeleteMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Boolean removeConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.removeConfig(namespace, configId).join();
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG)
    public Config getConfig(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfig(namespace, configId).join();
    }

    @PutMapping(RequestPathPrefix.CONFIGS_CONFIG_TO)
    public Boolean rollback(@PathVariable String namespace, @PathVariable String configId, @PathVariable int targetVersion) {
        return configService.rollback(namespace, configId, targetVersion).join();
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS)
    public List<ConfigVersion> getConfigVersions(@PathVariable String namespace, @PathVariable String configId) {
        return configService.getConfigVersions(namespace, configId).join();
    }

    @GetMapping(RequestPathPrefix.CONFIGS_CONFIG_VERSIONS_VERSION)
    public ConfigHistory getConfigHistory(@PathVariable String namespace, @PathVariable String configId, @PathVariable int version) {
        return configService.getConfigHistory(namespace, configId, version).join();
    }

}
