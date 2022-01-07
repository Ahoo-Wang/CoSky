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

import {Observable} from 'rxjs';
import {HttpClient, HttpEvent, HttpHeaders} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {ConfigHistoryDto} from './ConfigHistoryDto';
import {ConfigVersionDto} from './ConfigVersionDto';
import {ConfigDto} from './ConfigDto';

export type ImportPolicy = 'skip' | 'overwrite';

@Injectable({providedIn: 'root'})
export class ConfigClient {

  apiPrefix = environment.coskyRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {
  }

  getConfigs(namespace: string): Observable<string[]> {
    const apiUrl = this.getConfigsUrl(namespace);
    return this.httpClient.get<string[]>(apiUrl);
  }

  getConfigsUrl(namespace: string): string {
    return `${this.apiPrefix}/${namespace}/configs`;
  }

  getImportUrl(namespace: string): string {
    return this.getConfigsUrl(namespace);
  }

  getExportUrl(namespace: string, token: string): string {
    return `${this.getConfigsUrl(namespace)}/export?token=${token}`;
  }

  getConfigApiUrl(namespace: string, configId: string): string {
    return `${this.getConfigsUrl(namespace)}/${configId}`;
  }

  getConfig(namespace: string, configId: string): Observable<ConfigDto> {
    const apiUrl = this.getConfigApiUrl(namespace, configId);
    return this.httpClient.get<ConfigDto>(apiUrl);
  }

  setConfig(namespace: string, configId: string, data: string): Observable<boolean> {
    const apiUrl = this.getConfigApiUrl(namespace, configId);
    return this.httpClient.put<boolean>(apiUrl, data);
  }

  removeConfig(namespace: string, configId: string): Observable<boolean> {
    const apiUrl = this.getConfigApiUrl(namespace, configId);
    return this.httpClient.delete<boolean>(apiUrl);
  }

  rollback(namespace: string, configId: string, targetVersion: number): Observable<boolean> {
    const apiUrl = `${this.getConfigApiUrl(namespace, configId)}/to/${targetVersion}`;
    return this.httpClient.put<boolean>(apiUrl, null);
  }

  getConfigVersions(namespace: string, configId: string): Observable<ConfigVersionDto[]> {
    const apiUrl = `${this.getConfigApiUrl(namespace, configId)}/versions`;
    return this.httpClient.get<ConfigVersionDto[]>(apiUrl);
  }

  getConfigHistory(namespace: string, configId: string, version: number): Observable<ConfigHistoryDto> {
    const apiUrl = `${this.getConfigApiUrl(namespace, configId)}/versions/${version}`;
    return this.httpClient.get<ConfigHistoryDto>(apiUrl);
  }

  exportConfigs(namespace: string): Observable<HttpEvent<Blob>> {
    const apiUrl = `${this.getConfigsUrl(namespace)}/export`;

    return this.httpClient.get(apiUrl, {
      responseType: "blob", reportProgress: true, observe: "events", headers: new HttpHeaders(
        {'Content-Type': 'application/json'}
      )
    });
  }

}
