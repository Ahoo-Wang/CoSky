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
import {Injectable} from "@angular/core";
import {environment} from "../../../environments/environment";
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {ResourceActionDto} from "./ResourceActionDto";
import {RoleDto} from "./RoleDto";

@Injectable({providedIn: 'root'})
export class RoleClient {
  apiPrefix = environment.coskyRestApiHost + '/roles';

  constructor(private httpClient: HttpClient) {

  }

  getAllRole(): Observable<RoleDto[]> {
    return this.httpClient.get<RoleDto[]>(this.apiPrefix);
  }

  getResourceBind(roleName: string): Observable<ResourceActionDto[]> {
    const apiUrl = `${this.apiPrefix}/${roleName}/bind`;
    return this.httpClient.get<ResourceActionDto[]>(apiUrl);
  }

  saveRole(roleName: string, desc: string, resourceActionBind: ResourceActionDto[]): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${roleName}`;
    return this.httpClient.put<boolean>(apiUrl, {desc: desc, resourceActionBind});
  }

  removeRole(roleName: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${roleName}`;
    return this.httpClient.delete<boolean>(apiUrl);
  }
}
