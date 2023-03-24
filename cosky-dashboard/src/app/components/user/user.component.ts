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

import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {UserClient} from "../../api/user/UserClient";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {UserEditorComponent} from "./user-editor/user-editor.component";
import {UserAddComponent} from "./user-add/user-add.component";
import {Clone} from "../../util/Clone";
import {NzMessageService} from "ng-zorro-antd/message";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  users: UserDto[] = [];

  constructor(private userClient: UserClient, private drawerService: NzDrawerService, private messageService: NzMessageService) {
  }

  loadUsers() {
    this.userClient.query().subscribe(resp => {
      this.users = resp;
    })
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  removeUser(user: UserDto) {
    this.userClient.removeUser(user.id).subscribe(resp => {
      this.loadUsers();
    })
  }

  unlock(user: UserDto) {
    this.userClient.unlock(user.id).subscribe(resp => {
      this.messageService.success(`user:${user.id} unlock success!`)
    })
  }

  isSystem(user: UserDto) {
    return 'cosky' === user.id;
  }

  openAdd() {

    const drawerRef = this.drawerService.create<UserAddComponent, {}, string>({
      nzTitle: "Add User",
      nzWidth: '40%',
      nzContent: UserAddComponent
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterAdd.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.loadUsers();
      });
    });
  }

  openEditor(user: UserDto) {
    const drawerRef = this.drawerService.create<UserEditorComponent, {}, string>({
      nzTitle: `Edit User [${user.id}] Role`,
      nzWidth: '30%',
      nzContent: UserEditorComponent,
      nzContentParams: {
        user: Clone.deep(user)
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterSave.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.loadUsers();
      });
    });
  }
}
