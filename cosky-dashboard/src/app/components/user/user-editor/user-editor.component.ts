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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {UserDto} from "../../../api/user/UserDto";
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {UserClient} from "../../../api/user/UserClient";
import {RoleClient} from "../../../api/role/RoleClient";
import {RoleDto} from "../../../api/role/RoleDto";

@Component({
  selector: 'app-user-editor',
  templateUrl: './user-editor.component.html',
  styleUrls: ['./user-editor.component.scss']
})
export class UserEditorComponent implements OnInit {
  @Input() user!: UserDto;
  @Output() afterSave: EventEmitter<boolean> = new EventEmitter<boolean>();
  editorForm!: FormGroup;
  roles!: RoleDto[];

  constructor(private userClient: UserClient,
              private roleClient: RoleClient,
              private formBuilder: FormBuilder) {
  }

  ngOnInit(): void {
    this.roleClient.getAllRole().subscribe(resp => {
      this.roles = resp;
    })
    const controlsConfig = {
      roleBind: [this.user.roleBind, [Validators.required]]
    };
    this.editorForm = this.formBuilder.group(controlsConfig);
  }

  bindRole() {
    this.userClient.bindRole(this.user.username, this.user.roleBind).subscribe(bindResp => {
      this.afterSave.emit(true);
    })
  }

}
