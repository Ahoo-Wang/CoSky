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
import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {StatDto} from './StatDto';

@Injectable({providedIn: 'root'})
export class StatClient {
  apiPrefix = environment.coskyRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {

  }
  getStat(namespace: string): Observable<StatDto> {
    const apiUrl = `${this.apiPrefix}/${namespace}/stat`;
    return this.httpClient.get<StatDto>(apiUrl);
  }

  getTopology(namespace: string): Observable<Map<string,string[]>> {
    const apiUrl = `${this.apiPrefix}/${namespace}/stat/topology`;
    return this.httpClient.get<Map<string,string[]>>(apiUrl);
  }
}
