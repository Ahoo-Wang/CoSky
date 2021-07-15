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

import {HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from "@angular/common/http";
import {Observable, of} from "rxjs";
import {Injectable} from "@angular/core";
import {FORBIDDEN, LOGIN_PATH, SecurityService, UNAUTHORIZED} from "./SecurityService";
import {Router} from "@angular/router";
import {catchError, map, switchMap, switchMapTo} from "rxjs/operators";
import {NzMessageService} from 'ng-zorro-antd/message';


@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private securityService: SecurityService, private router: Router, private messageService: NzMessageService) {
  }

  ensureToken(req: HttpRequest<any>): HttpRequest<any> {
    let accessToken = this.securityService.getAccessToken();

    return req.clone({
      headers: req.headers.set('Authorization', accessToken)
    });
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {

    const authReq = this.ensureToken(req);

    return next.handle(authReq)
      /**
       * post check
       */
      .pipe(catchError((errorResponse: HttpErrorResponse, caught) => {

        if (errorResponse.status === UNAUTHORIZED) {
          return this.securityService.refreshToken()
            .pipe(switchMap(succeeded => {
              if (succeeded) {
                /**
                 * auto retry request after refreshToken
                 */
                const authReq = this.ensureToken(req);
                return next.handle(authReq);
              }
              this.router.navigate([LOGIN_PATH])
              throw errorResponse;
            }));
        }
        if (errorResponse.status === FORBIDDEN) {
          this.messageService.error("FORBIDDEN")
          throw errorResponse;
        }

        if (errorResponse.error) {
          this.messageService.error(errorResponse.error.msg)
        }

        throw errorResponse;
      }));
  }

}
