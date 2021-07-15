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
