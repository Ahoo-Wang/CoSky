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
import {HOME_PATH, SecurityService} from "../../security/SecurityService";
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {Router} from "@angular/router";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  username!: string;
  password!: string;

  constructor(private securityService: SecurityService
    , private router: Router
    , private formBuilder: FormBuilder) {
  }

  ngOnInit(): void {
    if (this.securityService.authenticated()) {
      this.router.navigate([HOME_PATH])
    }
    const controlsConfig = {
      username: [this.username, [Validators.required]],
      password: [this.password, [Validators.required]]
    };
    this.loginForm = this.formBuilder.group(controlsConfig);
  }


  signIn() {
    this.securityService.signIn(this.username, this.password);
  }
}
