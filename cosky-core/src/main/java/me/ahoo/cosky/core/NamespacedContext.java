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

package me.ahoo.cosky.core;

import com.google.common.base.Strings;

/**
 * @author ahoo wang
 */
public interface NamespacedContext extends Namespaced {

    /**
     * 全局命名空间上下文
     */
    NamespacedContext GLOBAL = of(CoSky.COSKY);

    /**
     * 设置当前上下文的命名空间
     *
     * @param namespace
     */
    void setCurrentContextNamespace(String namespace);

    default String getRequiredNamespace() {
        final String namespace = getNamespace();
        if (Strings.isNullOrEmpty(namespace)) {
            throw new CoskyException("namespace can not be empty!");
        }
        return namespace;
    }

    static NamespacedContext of(String namespace) {
        return new Default(namespace);
    }

    class Default implements NamespacedContext {
        private volatile String namespace;

        public Default(String namespace) {
            this.namespace = namespace;
        }

        /**
         * 设置当前上下文的命名空间
         *
         * @param namespace
         */
        @Override
        public void setCurrentContextNamespace(String namespace) {
            this.namespace = namespace;
        }

        /**
         * 获取当前上下文的命名空间
         *
         * @return
         */
        @Override
        public String getNamespace() {
            return namespace;
        }
    }
}
