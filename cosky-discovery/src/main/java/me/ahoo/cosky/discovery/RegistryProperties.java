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

package me.ahoo.cosky.discovery;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RegistryProperties {
    /**
     * instance time to live
     */
    private int instanceTtl = (int) TimeUnit.MINUTES.toSeconds(1);

    public int getInstanceTtl() {
        return instanceTtl;
    }

    public void setInstanceTtl(int instanceTtl) {
        this.instanceTtl = instanceTtl;
    }
}
