/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.discovery;

import lombok.var;
import me.ahoo.cosky.core.Consts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class DiscoveryKeyGeneratorTest {

    @Test
    public void getServiceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(Consts.COSKY);
        Assertions.assertEquals("cosky:svc_idx", serviceIdxKey);
    }

    @Test
    public void getServiceInstanceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getInstanceIdxKey(Consts.COSKY, "order_service");
        Assertions.assertEquals("cosky:svc_itc_idx:order_service", serviceIdxKey);
    }

    @Test
    public void getInstanceKey() {
        var instanceKey = DiscoveryKeyGenerator.getInstanceKey(Consts.COSKY, "http#127.0.0.1#8080@order_service");
        Assertions.assertEquals("cosky:svc_itc:http#127.0.0.1#8080@order_service", instanceKey);
    }
}
