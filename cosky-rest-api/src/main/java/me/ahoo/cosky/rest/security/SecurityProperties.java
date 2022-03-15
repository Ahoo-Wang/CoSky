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

package me.ahoo.cosky.rest.security;

import me.ahoo.cosky.core.CoSky;
import me.ahoo.cosky.rest.security.rbac.Action;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Security Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(SecurityProperties.PREFIX)
public class SecurityProperties {
    public static final String PREFIX = CoSky.COSKY + ".security";
    private boolean enabled = true;
    private boolean enforceInitSuperUser = false;
    private Jwt jwt;
    private AuditLog auditLog;
    
    public SecurityProperties() {
        jwt = new Jwt();
        auditLog = new AuditLog();
    }
    
    public boolean isEnforceInitSuperUser() {
        return enforceInitSuperUser;
    }
    
    public void setEnforceInitSuperUser(boolean enforceInitSuperUser) {
        this.enforceInitSuperUser = enforceInitSuperUser;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Jwt getJwt() {
        return jwt;
    }
    
    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }
    
    public AuditLog getAuditLog() {
        return auditLog;
    }
    
    public void setAuditLog(AuditLog auditLog) {
        this.auditLog = auditLog;
    }
    
    public static class Jwt {
        private String algorithm = "HmacSHA256";
        private String signingKey;
        private Duration accessTokenValidity = Duration.ofMinutes(10);
        private Duration refreshTokenValidity = Duration.ofDays(7);
        
        public String getAlgorithm() {
            return algorithm;
        }
        
        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
        
        public String getSigningKey() {
            return signingKey;
        }
        
        public void setSigningKey(String signingKey) {
            this.signingKey = signingKey;
        }
        
        public Duration getAccessTokenValidity() {
            return accessTokenValidity;
        }
        
        public void setAccessTokenValidity(Duration accessTokenValidity) {
            this.accessTokenValidity = accessTokenValidity;
        }
        
        public Duration getRefreshTokenValidity() {
            return refreshTokenValidity;
        }
        
        public void setRefreshTokenValidity(Duration refreshTokenValidity) {
            this.refreshTokenValidity = refreshTokenValidity;
        }
    }
    
    public static class AuditLog {
        private Action action = Action.WRITE;
        
        public Action getAction() {
            return action;
        }
        
        public void setAction(Action action) {
            this.action = action;
        }
    }
}
