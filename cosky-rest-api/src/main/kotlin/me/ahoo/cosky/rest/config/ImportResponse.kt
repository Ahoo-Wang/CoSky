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

package me.ahoo.cosky.rest.dto.config;

/**
 * Import Response.
 *
 * @author ahoo wang
 */
public class ImportResponse {
    private int total;
    private int succeeded;
    
    public ImportResponse() {
    }
    
    public ImportResponse(int total, int succeeded) {
        this.total = total;
        this.succeeded = succeeded;
    }
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
    
    public int getSucceeded() {
        return succeeded;
    }
    
    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }
    
}
