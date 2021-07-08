/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';

@Injectable({providedIn: 'root'})
export class NamespaceClient {
  apiPrefix = environment.coskyRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {

  }

  getNamespaces(): Observable<string[]> {
    return this.httpClient.get<string[]>(this.apiPrefix);
  }

  getCurrent(): Observable<string> {
    const apiUrl = this.apiPrefix + '/current';
    return this.httpClient.get<string>(apiUrl);
  }

  setCurrentContextNamespace(namespace: string): Observable<object> {
    const apiUrl = `${this.apiPrefix}/current/${namespace}`;
    return this.httpClient.put<object>(apiUrl, null);
  }


  setNamespace(namespace: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${namespace}`;
    return this.httpClient.put<boolean>(apiUrl, null);
  }

  removeNamespace(namespace: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${namespace}`;
    return this.httpClient.delete<boolean>(apiUrl);
  }


}
