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

package me.ahoo.cosky.rest.security.rbac;

import org.springframework.http.HttpMethod;

/**
 * Action.
 *
 * @author ahoo wang
 */
public enum Action {
    READ("r"),
    WRITE("w"),
    READ_WRITE("rw");
    
    private final String value;
    
    Action(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static Action of(String value) {
        switch (value) {
            case "r": {
                return READ;
            }
            case "w": {
                return WRITE;
            }
            case "rw": {
                return READ_WRITE;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + value);
        }
    }
    
    public static Action ofHttpMethod(String httpMethodStr) {
        HttpMethod httpMethod = HttpMethod.resolve(httpMethodStr);
        return ofHttpMethod(httpMethod);
    }
    
    public static Action ofHttpMethod(HttpMethod httpMethod) {
        switch (httpMethod) {
            case GET:
            case OPTIONS:
            case TRACE:
            case HEAD:
                return READ;
            case POST:
            case PUT:
            case DELETE:
            case PATCH:
                return WRITE;
            default:
                throw new IllegalStateException("Unexpected value: " + httpMethod);
        }
    }
    
    public boolean check(Action requestAction) {
        if (READ_WRITE.value.equals(this.value)) {
            return true;
        }
        return this.equals(requestAction);
    }
    
    
}
