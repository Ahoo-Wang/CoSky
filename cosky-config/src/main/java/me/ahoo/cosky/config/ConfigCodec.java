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

package me.ahoo.cosky.config;


import java.util.Map;

/**
 * @author ahoo wang
 */
public class ConfigCodec {

    private static final String CONFIG_ID = "configId";
    private static final String DATA = "data";
    private static final String HASH = "hash";
    private static final String VERSION = "version";
    private static final String CREATE_TIME = "createTime";
    private static final String OP = "op";
    private static final String OP_TIME = "opTime";

    public static <T extends Config> Config decodeConfig(T config, Map<String, String> configData) {
        config.setConfigId(configData.get(CONFIG_ID));
        config.setData(configData.get(DATA));
        config.setHash(configData.get(HASH));
        config.setVersion(Integer.parseInt(configData.get(VERSION)));
        config.setCreateTime(Long.parseLong(configData.get(CREATE_TIME)));
        return config;
    }

    public static Config decode(Map<String, String> configData) {
        Config config = new Config();
        return decodeConfig(config, configData);
    }

    public static ConfigHistory decodeHistory(Map<String, String> configData) {
        ConfigHistory configHistory = new ConfigHistory();
        decodeConfig(configHistory, configData);
        configHistory.setOp(configData.get(OP));
        configHistory.setOpTime(Long.parseLong(configData.get(OP_TIME)));
        return configHistory;
    }
}
