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
import {UserDto} from "./UserDto";

@Injectable({providedIn: 'root'})
export class UserClient {
  apiPrefix = environment.coskyRestApiHost + '/users';

  constructor(private httpClient: HttpClient) {

  }

  query(): Observable<UserDto[]> {
    return this.httpClient.get<UserDto[]>(this.apiPrefix);
  }

  changePwd(username: string, oldPassword: string, newPassword: string): Observable<void> {
    const apiUrl = `${this.apiPrefix}/${username}/password`;
    return this.httpClient.patch<void>(apiUrl, {username, oldPassword, newPassword});
  }

  addUser(username: string, password: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${username}`;
    return this.httpClient.post<boolean>(apiUrl, {password});
  }

  removeUser(username: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${username}`;
    return this.httpClient.delete<boolean>(apiUrl);
  }

  bindRole(username: string, roleBind: string[]): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${username}/role`;
    return this.httpClient.patch<boolean>(apiUrl, roleBind);
  }

  unlock(username: string): Observable<void> {
    const apiUrl = `${this.apiPrefix}/${username}/unlock`;
    return this.httpClient.delete<void>(apiUrl);
  }
}
