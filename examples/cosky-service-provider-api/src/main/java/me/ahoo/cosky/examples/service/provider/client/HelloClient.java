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

package me.ahoo.cosky.examples.service.provider.client;

import me.ahoo.cosky.examples.service.provider.Constants;
import me.ahoo.cosky.examples.service.provider.api.HelloApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author ahoo wang
 */
@FeignClient(name = Constants.SERVICE_NAME, contextId = Constants.SERVICE_NAME_PREFIX + "HelloClient", path = HelloApi.PATH)
public interface HelloClient extends HelloApi {
}
