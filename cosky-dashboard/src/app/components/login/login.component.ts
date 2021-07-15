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
