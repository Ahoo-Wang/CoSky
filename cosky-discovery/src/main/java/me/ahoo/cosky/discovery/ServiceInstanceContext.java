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

package me.ahoo.cosky.discovery;

import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.core.NamespacedContext;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author ahoo wang
 */
public interface ServiceInstanceContext extends Namespaced {

    ServiceInstanceContext CURRENT = new CurrentContext();

    void setServiceInstance(ServiceInstance serviceInstance);

    void addListener(ServiceInstanceContextChangedListener changedListener);

    void removeListener(ServiceInstanceContextChangedListener changedListener);

    ServiceInstance getServiceInstance();

    class CurrentContext implements ServiceInstanceContext {

        private volatile ServiceInstance serviceInstance;
        private final CopyOnWriteArraySet<ServiceInstanceContextChangedListener> listeners;

        public CurrentContext() {
            this.listeners = new CopyOnWriteArraySet<>();
        }

        /**
         * 获取当前上下文的命名空间
         *
         * @return
         */
        @Override
        public String getNamespace() {
            return NamespacedContext.GLOBAL.getNamespace();
        }

        @Override
        public void setServiceInstance(ServiceInstance serviceInstance) {
            final ServiceInstance before = this.serviceInstance;
            this.serviceInstance = serviceInstance;
            listeners.forEach(changedListener -> changedListener.onChange(before, serviceInstance));
        }

        @Override
        public void addListener(ServiceInstanceContextChangedListener changedListener) {
            listeners.add(changedListener);
        }

        @Override
        public void removeListener(ServiceInstanceContextChangedListener changedListener) {
            listeners.remove(changedListener);
        }

        @Override
        public ServiceInstance getServiceInstance() {
            return this.serviceInstance;
        }
    }

    @FunctionalInterface
    interface ServiceInstanceContextChangedListener {
        void onChange(ServiceInstance before, ServiceInstance after);
    }
}
