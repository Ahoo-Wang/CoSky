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
import {HttpErrorResponse} from "@angular/common/http";
import {NzMessageService} from "ng-zorro-antd/message";
import {Router} from "@angular/router";

const ACCESS_TOKEN_KEY = "cosky:accessToken"
const REFRESH_TOKEN_KEY = "cosky:refreshToken"


@Injectable({providedIn: 'root'})
export class SecurityService {
  redirectFrom: string = 'dashboard';

  constructor(private authenticateClient: AuthenticateClient
    , private messageService: NzMessageService
    , private router: Router) {
  }

  signIn(username: string, password: string) {
    this.authenticateClient.login(username, password).subscribe((resp) => {
        this.setToken(resp);
        this.router.navigate([this.redirectFrom])
      },
      ((errorResponse: HttpErrorResponse) => {
        if (errorResponse.error) {
          this.messageService.error(errorResponse.error.msg)
        }
        console.error(errorResponse)
      }))
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
    /**
     * check tokenSplit.length===3
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
    if (!accessToken || !refreshToken) {
      return throwError('accessToken or refreshToken is empty!')
    }
    return this.authenticateClient
      .refresh(accessToken, refreshToken)
      .pipe(
        map(resp => {
          this.setToken(resp);
          return true;
        }),
        catchError((err, caught) => {
          console.log(err);
          return of(false);
        })
      );
  }

  signOut() {
    localStorage.clear();
    this.router.navigate(['login'])
  }

}
