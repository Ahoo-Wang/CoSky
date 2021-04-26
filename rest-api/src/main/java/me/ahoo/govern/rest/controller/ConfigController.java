package me.ahoo.govern.rest.controller;

import me.ahoo.govern.config.Config;
import me.ahoo.govern.config.ConfigHistory;
import me.ahoo.govern.config.ConfigService;
import me.ahoo.govern.config.ConfigVersion;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("/v1/configs")
public class ConfigController {
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }


    @GetMapping
    public Set<String> getConfigs() {
        return configService.getConfigs().join();
    }

    @PutMapping("/{configId}")
    public Boolean setConfig(@PathVariable String configId, @RequestBody String data) {
        return configService.setConfig(configId, data).join();
    }

    @DeleteMapping("/{configId}")
    public Boolean removeConfig(@PathVariable String configId) {
        return configService.removeConfig(configId).join();
    }

    @GetMapping("/{configId}")
    public Config getConfig(@PathVariable String configId) {
        return configService.getConfig(configId).join();
    }

    @PutMapping("/{configId}/rollback/{targetVersion}")
    public Boolean rollback(@PathVariable String configId, @PathVariable int targetVersion) {
        return configService.rollback(configId, targetVersion).join();
    }

    @GetMapping("/{configId}/versions")
    public List<ConfigVersion> getConfigVersions(@PathVariable String configId) {
        return configService.getConfigVersions(configId).join();
    }

    @GetMapping("/{configId}/history/{version}")
    public ConfigHistory getConfigHistory(@PathVariable String configId, @PathVariable int version) {
        return configService.getConfigHistory(configId, version).join();
    }

}
