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
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {LoginResponse} from "./LoginResponse";


@Injectable({providedIn: 'root'})
export class AuthenticateClient {
  apiPrefix = environment.coskyRestApiHost + '/authenticate';

  constructor(private httpClient: HttpClient) {

  }

  login(username: string, password: string): Observable<LoginResponse> {
    const apiUrl = `${this.apiPrefix}/login`;
    return this.httpClient.post<LoginResponse>(apiUrl, {username, password});
  }

  refresh(accessToken: string, refreshToken: string): Observable<LoginResponse> {
    const apiUrl = `${this.apiPrefix}/refresh`;
    return this.httpClient.post<LoginResponse>(apiUrl, {accessToken, refreshToken});
  }
}