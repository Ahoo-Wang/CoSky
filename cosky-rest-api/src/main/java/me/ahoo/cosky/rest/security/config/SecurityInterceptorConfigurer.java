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

package me.ahoo.cosky.rest.security.config;

import me.ahoo.cosky.rest.security.AuthorizeHandlerInterceptor;
import me.ahoo.cosky.rest.security.ConditionalOnSecurityEnabled;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author ahoo wang
 */
@Configuration
@ConditionalOnSecurityEnabled
public class SecurityInterceptorConfigurer implements WebMvcConfigurer {

    private final AuthorizeHandlerInterceptor authorizeHandlerInterceptor;

    public SecurityInterceptorConfigurer(AuthorizeHandlerInterceptor authorizeHandlerInterceptor) {
        this.authorizeHandlerInterceptor = authorizeHandlerInterceptor;
    }

    /**
     * Override this method to add Spring MVC interceptors for
     * pre- and post-processing of controller invocation.
     *
     * @param registry
     * @see InterceptorRegistry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizeHandlerInterceptor)
                .excludePathPatterns(
                        "/swagger-ui/**"
                        , "/swagger-resources/**"
                        , "/v3/api-docs"
                        , "/dashboard/**"
                        , RequestPathPrefix.AUTHENTICATE_PREFIX + "/**"
                )
                .addPathPatterns("/**");
    }
}
