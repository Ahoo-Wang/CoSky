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

import {Token} from "../api/authenticate/Token";
import {TokenPayload} from "../api/authenticate/TokenPayload";
import {AuthenticateClient} from "../api/authenticate/AuthenticateClient";
import {Observable, of, throwError} from "rxjs";
import {catchError, map} from "rxjs/operators";
import {NzMessageService} from "ng-zorro-antd/message";
import {Router} from "@angular/router";

export const UNAUTHORIZED = 401;
export const FORBIDDEN = 403;

export const ACCESS_TOKEN_KEY = "cosky:accessToken"
export const REFRESH_TOKEN_KEY = "cosky:refreshToken"
export const LOGIN_PATH = 'login';
export const HOME_PATH = 'home';
const USER_UNAUTHORIZED: TokenPayload = {
  jti: "",
  sub: "UNAUTHORIZED",
  role: "",
  iat: 0,
  exp: 0
};

@Injectable({providedIn: 'root'})
export class SecurityService {
  redirectFrom: string = HOME_PATH;

  constructor(private authenticateClient: AuthenticateClient
    , private messageService: NzMessageService
    , private router: Router) {
  }

  signIn(username: string, password: string) {
    this.authenticateClient.login(username, password).subscribe((resp) => {
      this.setToken(resp);
      this.router.navigate([this.redirectFrom])
    });
  }

  getAccessToken(): string {
    let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (accessToken) {
      return accessToken;
    }
    return '';
  }

  setToken(token: Token) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, token.refreshToken);
  }

  getCurrentUser(): TokenPayload {
    let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (accessToken) {
      return this.parseToken(accessToken)
    }
    return USER_UNAUTHORIZED;
  }

  authenticated(): boolean {
    const accessToken = this.getAccessToken();
    if (accessToken.length === 0) {
      return false;
    }
    return this.isValidity(accessToken);
  }

  getCurrentTimeOfSecond() {
    return Date.now() / 1000;
  }

  isValidity(token: string) {
    const tokenExp = this.parseToken(token).exp;
    return tokenExp > this.getCurrentTimeOfSecond();
  }

  parseToken(token: string): TokenPayload {
    let tokenSplit = token.split(".");
    if (tokenSplit.length !== 3) {
      throw Error(`token format error:[${token}]`);
    }
    /**
     *
     * TODO atob bug
     * Uncaught DOMException: Failed to execute 'atob' on 'Window': The string to be decoded is not correctly encoded.
     * at <anonymous>:1:1
     */
    const payloadStr = atob(tokenSplit[1]);

    return JSON.parse(payloadStr);
  }

  refreshValid(): boolean {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return false;
    }
    return this.isValidity(refreshToken);
  }

  refreshToken(): Observable<boolean> {
    const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

    if (!accessToken || !refreshToken || !this.refreshValid()) {
      return of(false);
    }

    return this.authenticateClient
      .refresh(accessToken, refreshToken)
      .pipe(
        map(resp => {
          this.setToken(resp);
          return true;
        }),
        catchError((err, caught) => {
          this.clearToken();
          return of(false);
        })
      );
  }

  signOut() {
    this.clearToken();
    this.router.navigate([LOGIN_PATH])
  }

  clearToken() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }
}
