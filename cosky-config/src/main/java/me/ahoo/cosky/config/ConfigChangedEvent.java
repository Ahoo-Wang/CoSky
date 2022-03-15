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

package me.ahoo.cosky.config;

import java.util.Locale;

/**
 * Config Event .
 *
 * @author ahoo wang
 */
public class ConfigChangedEvent {
    
    private final NamespacedConfigId namespacedConfigId;
    private final Event event;
    
    public ConfigChangedEvent(NamespacedConfigId namespacedConfigId, Event event) {
        this.namespacedConfigId = namespacedConfigId;
        this.event = event;
    }
    
    public NamespacedConfigId getNamespacedConfigId() {
        return namespacedConfigId;
    }
    
    public Event getEvent() {
        return event;
    }
    
    public enum Event {
        SET,
        ROLLBACK,
        REMOVE;
        
        public static Event of(String op) {
            switch (op.toUpperCase(Locale.ROOT)) {
                case "SET":
                    return SET;
                case "ROLLBACK":
                    return ROLLBACK;
                case "REMOVE":
                    return REMOVE;
                default:
                    throw new IllegalStateException("Unexpected value: " + op.toUpperCase(Locale.ROOT));
            }
        }
    }
}
