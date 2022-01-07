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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ResourceActionDto} from "../../../api/role/ResourceActionDto";
import {NamespaceClient} from "../../../api/namespace/NamespaceClient";
import {RoleClient} from "../../../api/role/RoleClient";
import {RoleDto} from "../../../api/role/RoleDto";
import {FormBuilder, FormGroup, Validators} from "@angular/forms";

@Component({
  selector: 'app-role-editor',
  templateUrl: './role-editor.component.html',
  styleUrls: ['./role-editor.component.scss']
})
export class RoleEditorComponent implements OnInit {
  @Input() role!: RoleDto | null;
  @Output() afterSave: EventEmitter<boolean> = new EventEmitter<boolean>();

  resourceActionBind: ResourceActionDto[] = [];
  namespaces: string[] = [];
  roleName!: string;
  desc!: string;
  editorForm!: FormGroup;
  isAdd!: boolean;

  constructor(private namespaceClient: NamespaceClient, private roleClient: RoleClient,
              private formBuilder: FormBuilder) {

  }

  loadRole() {
    if (!this.role) {
      this.isAdd = true;
      return;
    }
    this.isAdd = false;
    this.roleName = this.role.name;
    this.desc = this.role.desc;
    this.roleClient.getResourceBind(this.roleName).subscribe(resp => {
      this.resourceActionBind = resp;
    })
  }

  ngOnInit(): void {
    this.loadRole();
    const controlsConfig = {
      roleName: [this.roleName, [Validators.required]],
      desc: [this.desc, [Validators.required]]
    };
    if (!this.isAdd) {
      controlsConfig.roleName = [this.roleName];
    }
    this.editorForm = this.formBuilder.group(controlsConfig);
    this.namespaceClient.getNamespaces().subscribe(resp => this.namespaces = resp);
  }


  addResourceAction() {
    this.resourceActionBind = [...this.resourceActionBind, {namespace: '', action: 'r'}]
  }

  removeResourceAction(resourceAction: ResourceActionDto) {
    this.resourceActionBind = this.resourceActionBind.filter(resource => resource != resourceAction);
  }

  saveRole() {
    this.roleClient.saveRole(this.roleName, this.desc, this.resourceActionBind).subscribe(resp => {
      this.afterSave.emit(true)
    })
  }
}
