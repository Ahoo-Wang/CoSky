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

import java.util.Locale;

/**
 * Service Changed Event.
 *
 * @author ahoo wang
 */
public class ServiceChangedEvent {
    
    public static final String REGISTER = "register";
    public static final String DEREGISTER = "deregister";
    public static final String EXPIRED = "expired";
    public static final String RENEW = "renew";
    public static final String SET_METADATA = "set_metadata";
    
    private final NamespacedServiceId namespacedServiceId;
    private final Event event;
    private final Instance instance;
    
    public ServiceChangedEvent(NamespacedServiceId namespacedServiceId, Event event, Instance instance) {
        this.namespacedServiceId = namespacedServiceId;
        this.event = event;
        this.instance = instance;
    }
    
    public NamespacedServiceId getNamespacedServiceId() {
        return namespacedServiceId;
    }
    
    public Event getEvent() {
        return event;
    }
    
    public Instance getInstance() {
        return instance;
    }
    
    public static ServiceChangedEvent of(NamespacedServiceId namespacedServiceId, Event event, Instance instance) {
        return new ServiceChangedEvent(namespacedServiceId, event, instance);
    }
    
    public enum Event {
        REGISTER("register"), DEREGISTER("deregister"), EXPIRED("expired"), RENEW("renew"), SET_METADATA("set_metadata");
        private final String op;
        
        Event(String op) {
            this.op = op;
        }
        
        public String getOp() {
            return op;
        }
        
        public static Event of(String op) {
            switch (op.toUpperCase(Locale.ROOT)) {
                case "REGISTER":
                    return REGISTER;
                case "DEREGISTER":
                    return DEREGISTER;
                case "EXPIRED":
                    return EXPIRED;
                case "RENEW":
                    return RENEW;
                case "SET_METADATA":
                    return SET_METADATA;
                default:
                    throw new IllegalStateException("Unexpected value: " + op.toUpperCase(Locale.ROOT));
            }
        }
    }
}
