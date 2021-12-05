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

import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {UserClient} from "../../../api/user/UserClient";
import {SecurityService} from "../../../security/SecurityService";

@Component({
  selector: 'app-user-change-pwd',
  templateUrl: './user-change-pwd.component.html',
  styleUrls: ['./user-change-pwd.component.scss']
})
export class UserChangePwdComponent implements OnInit {
  editorForm!: FormGroup;
  oldPassword!: string;
  newPassword!: string;
  @Output() afterChange: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private userClient: UserClient,
              private securityService: SecurityService,
              private formBuilder: FormBuilder) {
  }

  ngOnInit(): void {
    const controlsConfig = {
      oldPassword: [this.oldPassword, [Validators.required]],
      newPassword: [this.newPassword, [Validators.required]]
    };
    this.editorForm = this.formBuilder.group(controlsConfig);
  }

  changePwd() {
    const username = this.securityService.getCurrentUser().sub;
    this.userClient.changePwd(username, this.oldPassword, this.newPassword).subscribe(result => {
      this.afterChange.emit(true);
    });
  }
}
