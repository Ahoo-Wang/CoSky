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

package me.ahoo.cosky.core.redis;

import java.time.Duration;

/**
 * @author ahoo wang
 */
public class RedisConfig {
    private String url;
    private RedisMode mode;
    private ReadFrom readFrom;
    private ClusterConfig cluster = new ClusterConfig();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public RedisMode getMode() {
        return mode;
    }

    public void setMode(RedisMode mode) {
        this.mode = mode;
    }

    public ReadFrom getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(ReadFrom readFrom) {
        this.readFrom = readFrom;
    }

    public ClusterConfig getCluster() {
        return cluster;
    }

    public void setCluster(ClusterConfig cluster) {
        this.cluster = cluster;
    }

    public enum RedisMode {
        STANDALONE,
        CLUSTER
    }

    public enum ReadFrom {
        MASTER,
        MASTER_PREFERRED,
        UPSTREAM,
        UPSTREAM_PREFERRED,
        REPLICA_PREFERRED,
        REPLICA,
        NEAREST,
        ANY,
        ANY_REPLICA
    }

    public static class ClusterConfig {
        private Boolean refreshClusterView = true;
        private Duration refreshPeriod = Duration.ofSeconds(30);

        public Boolean getRefreshClusterView() {
            return refreshClusterView;
        }

        public void setRefreshClusterView(Boolean refreshClusterView) {
            this.refreshClusterView = refreshClusterView;
        }

        public Duration getRefreshPeriod() {
            return refreshPeriod;
        }

        public void setRefreshPeriod(Duration refreshPeriod) {
            this.refreshPeriod = refreshPeriod;
        }
    }
}
