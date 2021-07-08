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

import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {ServiceInstanceDto} from './ServiceInstanceDto';
import {ServiceStatDto} from './ServiceStatDto';

@Injectable({providedIn: 'root'})

export class ServiceClient {
  apiPrefix = environment.coskyRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {

  }

  getServicesPrefix(namespace: string): string {
    return `${this.apiPrefix}/${namespace}/services`;
  }

  getServices(namespace: string): Observable<string[]> {
    const apiUrl = this.getServicesPrefix(namespace);
    return this.httpClient.get<string[]>(apiUrl);
  }

  getServiceApiUrl(namespace: string, serviceId: string): string {
    return `${this.getServicesPrefix(namespace)}/${serviceId}`;
  }

  getInstancesApiUrl(namespace: string, serviceId: string): string {
    return `${this.getServiceApiUrl(namespace, serviceId)}/instances`;
  }

  addService(namespace: string, serviceId: string): Observable<boolean> {
    const apiUrl = this.getServiceApiUrl(namespace, serviceId);
    return this.httpClient.put<boolean>(apiUrl, null);
  }

  removeService(namespace: string, serviceId: string): Observable<boolean> {
    const apiUrl = this.getServiceApiUrl(namespace, serviceId);
    return this.httpClient.delete<boolean>(apiUrl);
  }

  getInstances(namespace: string, serviceId: string): Observable<ServiceInstanceDto[]> {
    const apiUrl = this.getInstancesApiUrl(namespace, serviceId);
    return this.httpClient.get<ServiceInstanceDto[]>(apiUrl);
  }

  register(namespace: string, serviceId: string, instance: ServiceInstanceDto): Observable<boolean> {
    const apiUrl = this.getInstancesApiUrl(namespace, serviceId);
    return this.httpClient.put<boolean>(apiUrl, instance);
  }

  deregister(namespace: string, serviceId: string, instanceId: string): Observable<boolean> {
    const apiUrl = `${this.getInstancesApiUrl(namespace, serviceId)}/${encodeURIComponent(instanceId)}`;
    return this.httpClient.delete<boolean>(apiUrl);
  }

  setMetadata(namespace: string, serviceId: string, instanceId: string, metadata: Map<string, string>): Observable<boolean> {
    const apiUrl = `${this.getInstancesApiUrl(namespace, serviceId)}/${encodeURIComponent(instanceId)}/metadata`;
    return this.httpClient.put<boolean>(apiUrl, metadata);
  }

  getServiceStats(namespace: string): Observable<ServiceStatDto[]> {
    const apiUrl = `${this.getServicesPrefix(namespace)}/stats`;
    return this.httpClient.get<ServiceStatDto[]>(apiUrl);
  }

}
