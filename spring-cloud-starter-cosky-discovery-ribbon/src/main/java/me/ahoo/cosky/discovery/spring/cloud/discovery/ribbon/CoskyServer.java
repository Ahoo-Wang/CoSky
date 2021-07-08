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

package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.loadbalancer.Server;
import me.ahoo.cosky.discovery.ServiceInstance;

/**
 * @author ahoo wang
 */
public class CoskyServer extends Server {
    private final ServiceInstance instance;
    private final MetaInfo metaInfo;

    public CoskyServer(final ServiceInstance instance) {
        super(instance.getSchema(), instance.getHost(), instance.getPort());
        this.instance = instance;
        this.metaInfo = new MetaInfo() {
            @Override
            public String getAppName() {
                return instance.getServiceId();
            }

            @Override
            public String getServerGroup() {
                return null;
            }

            @Override
            public String getServiceIdForDiscovery() {
                return null;
            }

            @Override
            public String getInstanceId() {
                return instance.getInstanceId();
            }
        };
    }

    @Override
    public MetaInfo getMetaInfo() {
        return metaInfo;
    }

    public ServiceInstance getInstance() {
        return instance;
    }
}
