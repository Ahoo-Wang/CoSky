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

import com.google.common.base.Objects;

/**
 * Config model.
 *
 * @author ahoo wang
 */
public class Config extends ConfigVersion {
    
    private String data;
    /**
     * data hash.
     */
    private String hash;
    private long createTime;
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Config)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Config config = (Config) o;
        return createTime == config.createTime && Objects.equal(data, config.data) && Objects.equal(hash, config.hash);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), data, hash, createTime);
    }
}
