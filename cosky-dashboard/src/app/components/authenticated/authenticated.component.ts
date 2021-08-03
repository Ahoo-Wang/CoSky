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

import {Component, OnInit} from '@angular/core';
import {SecurityService} from "../../security/SecurityService";
import {TokenPayload} from "../../api/authenticate/TokenPayload";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {UserChangePwdComponent} from "../user/user-change-pwd/user-change-pwd.component";
import {NzMessageService} from "ng-zorro-antd/message";

@Component({
  selector: 'app-authenticated',
  templateUrl: './authenticated.component.html',
  styleUrls: ['./authenticated.component.scss']
})
export class AuthenticatedComponent implements OnInit {
  title = 'CoSky Dashboard';
  isCollapsed = false;
  currentUser: TokenPayload;

  constructor(private securityService: SecurityService
    , private messageService: NzMessageService
    , private drawerService: NzDrawerService) {
    this.currentUser = this.securityService.getCurrentUser();
  }

  ngOnInit(): void {

  }


  signOut() {
    this.securityService.signOut();
  }


  openChangePwd() {
    const drawerRef = this.drawerService.create<UserChangePwdComponent, {}, string>({
      nzTitle: `Change User:[${this.currentUser.sub}] Password`,
      nzWidth: '30%',
      nzContent: UserChangePwdComponent
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterChange.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.messageService.success("Password reset complete!")
      });
    });
  }
}
