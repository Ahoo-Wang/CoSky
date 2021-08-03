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

import {Injectable} from "@angular/core";
import {environment} from "../../../environments/environment";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {QueryLogResponse} from "./QueryLogResponse";

@Injectable({providedIn: 'root'})
export class AuditLogClient {
  apiPrefix = environment.coskyRestApiHost + '/audit-log';

  constructor(private httpClient: HttpClient) {

  }

  queryLog(offset: number, limit: number): Observable<QueryLogResponse> {
    const apiUrl = `${this.apiPrefix}?offset=${offset}&limit=${limit}`;
    return this.httpClient.get<QueryLogResponse>(apiUrl);
  }
}
