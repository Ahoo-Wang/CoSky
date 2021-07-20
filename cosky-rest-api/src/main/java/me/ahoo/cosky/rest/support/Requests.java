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

package me.ahoo.cosky.rest.support;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * @author ahoo wang
 */
public final class Requests {

    public final static String X_REAL_IP = "X-Real-IP";
    public final static String X_FORWARDED_FOR = "X-Forwarded-For";

    private Requests() {
    }

    public static String getRealIp(HttpServletRequest request) {

        Object clientIp = request.getHeader(X_REAL_IP);
        if (Objects.nonNull(clientIp)) {
            return clientIp.toString();
        }
        clientIp = request.getHeader(X_FORWARDED_FOR);
        if (Objects.nonNull(clientIp)) {
            return clientIp.toString();
        }
        return request.getRemoteHost();
    }
}
