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
import {RoleClient} from "../../api/role/RoleClient";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {RoleEditorComponent} from "./role-editor/role-editor.component";
import {RoleDto} from "../../api/role/RoleDto";

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit {
  roles: RoleDto[] = [];

  constructor(private roleClient: RoleClient, private drawerService: NzDrawerService) {
  }

  loadRoles() {
    this.roleClient.getAllRole().subscribe(resp => {
      this.roles = resp;
    })
  }

  ngOnInit(): void {
    this.loadRoles();
  }

  removeRole(role: RoleDto) {
    this.roleClient.removeRole(role.name).subscribe(resp => {
      this.loadRoles();
    })
  }

  isSystem(role: RoleDto) {
    return 'admin' === role.name;
  }


  openEditor(role: RoleDto | null) {
    const title = role ? `Edit Role [${role.name}]` : 'Add Role';
    const drawerRef = this.drawerService.create<RoleEditorComponent, {}, string>({
      nzTitle: title,
      nzWidth: '60vw',
      nzContent: RoleEditorComponent,
      nzContentParams: {
        role
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterSave.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.loadRoles();
      });
    });
  }
}
