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

package me.ahoo.cosky.config;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 */
public interface ConfigRollback {
    int HISTORY_SIZE = 10;

    CompletableFuture<Boolean> rollback(String configId, int targetVersion);

    CompletableFuture<Boolean> rollback(String namespace, String configId, int targetVersion);

    CompletableFuture<List<ConfigVersion>> getConfigVersions(String configId);

    CompletableFuture<List<ConfigVersion>> getConfigVersions(String namespace, String configId);

    CompletableFuture<ConfigHistory> getConfigHistory(String configId, int version);

    CompletableFuture<ConfigHistory> getConfigHistory(String namespace, String configId, int version);
}
