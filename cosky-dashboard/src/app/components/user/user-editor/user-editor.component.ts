import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {UserDto} from "../../../api/user/UserDto";
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {UserClient} from "../../../api/user/UserClient";
import {RoleClient} from "../../../api/role/RoleClient";

@Component({
  selector: 'app-user-editor',
  templateUrl: './user-editor.component.html',
  styleUrls: ['./user-editor.component.scss']
})
export class UserEditorComponent implements OnInit {
  @Input() user!: UserDto | null;
  @Output() afterSave: EventEmitter<boolean> = new EventEmitter<boolean>();
  editorForm!: FormGroup;
  username!: string;
  password!: string;
  roleBind!: string[];
  roles!: string[];

  constructor(private userClient: UserClient,
              private roleClient: RoleClient,
              private formBuilder: FormBuilder) {
  }

  ngOnInit(): void {
    if (this.user) {
      this.username = this.user.username;
      this.roleBind = this.user.roleBind;
    }
    this.roleClient.getAllRole().subscribe(resp => {
      this.roles = resp;
    })
    const controlsConfig = {
      username: [this.username, [Validators.required]],
      password: [this.password, [Validators.required]]
    };
    this.editorForm = this.formBuilder.group(controlsConfig);
  }

  addUser() {
    this.userClient.addUser(this.username, this.password).subscribe(resp => {
      this.userClient.bindRole(this.username, this.roleBind).subscribe(bindResp => {
        this.afterSave.emit(true);
      })
    });
  }

}
