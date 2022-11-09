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
package me.ahoo.cosky.core

/**
 * Namespaced.
 *
 * @author ahoo wang
 */
interface Namespaced {
    /**
     * 获取当前上下文的命名空间.
     *
     * @return current namespace
     */
    val namespace: String
        get() = DEFAULT

    companion object {
        const val DEFAULT: String = "${CoSky.COSKY}-{default}"
        const val SYSTEM: String = "${CoSky.COSKY}-{system}"
    }
}
