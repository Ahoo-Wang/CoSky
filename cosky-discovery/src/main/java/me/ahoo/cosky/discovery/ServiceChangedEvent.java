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

/**
 * @author ahoo wang
 */
public class ServiceChangedEvent {

    public static final String REGISTER = "register";
    public static final String DEREGISTER = "deregister";
    public static final String EXPIRED = "expired";
    public static final String RENEW = "renew";
    public static final String SET_METADATA = "set_metadata";

    private final NamespacedServiceId namespacedServiceId;
    private final String op;
    private final Instance instance;

    public ServiceChangedEvent(NamespacedServiceId namespacedServiceId, String op, Instance instance) {
        this.namespacedServiceId = namespacedServiceId;
        this.op = op;
        this.instance = instance;
    }

    public NamespacedServiceId getNamespacedServiceId() {
        return namespacedServiceId;
    }

    public String getOp() {
        return op;
    }

    public Instance getInstance() {
        return instance;
    }

    public static ServiceChangedEvent of(NamespacedServiceId namespacedServiceId, String op, Instance instance) {
        return new ServiceChangedEvent(namespacedServiceId, op, instance);
    }

}
