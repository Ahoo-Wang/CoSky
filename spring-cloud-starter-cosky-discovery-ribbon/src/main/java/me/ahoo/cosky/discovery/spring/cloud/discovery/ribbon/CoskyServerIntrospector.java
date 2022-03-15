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

package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.loadbalancer.Server;
import org.springframework.cloud.netflix.ribbon.DefaultServerIntrospector;

import java.util.Map;

/**
 * Cosky Server Introspector.
 *
 * @author ahoo wang
 */
public class CoskyServerIntrospector extends DefaultServerIntrospector {
    
    @Override
    public boolean isSecure(Server server) {
        if (server instanceof CoskyServer) {
            return ((CoskyServer) server).getInstance().isSecure();
        }
        return super.isSecure(server);
    }
    
    @Override
    public Map<String, String> getMetadata(Server server) {
        if (server instanceof CoskyServer) {
            return ((CoskyServer) server).getInstance().getMetadata();
        }
        return super.getMetadata(server);
    }
}
