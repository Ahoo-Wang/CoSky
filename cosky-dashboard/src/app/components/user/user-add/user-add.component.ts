import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from "@angular/forms";
import {UserClient} from "../../../api/user/UserClient";
import {RoleClient} from "../../../api/role/RoleClient";
import {RoleDto} from "../../../api/role/RoleDto";

@Component({
  selector: 'app-user-add',
  templateUrl: './user-add.component.html',
  styleUrls: ['./user-add.component.scss']
})
export class UserAddComponent implements OnInit {
  @Output() afterAdd: EventEmitter<boolean> = new EventEmitter<boolean>();
  addForm!: FormGroup;
  username!: string;
  password!: string;
  roleBind!: string[];
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
      username: [this.username, [Validators.required]],
      password: [this.password, [Validators.required]],
      roleBind: [this.roleBind, [Validators.required]]
    };
    this.addForm = this.formBuilder.group(controlsConfig);
  }

  addUser() {
    this.userClient.addUser(this.username, this.password).subscribe(resp => {
      this.userClient.bindRole(this.username, this.roleBind).subscribe(bindResp => {
        this.afterAdd.emit(true);
      })
    });
  }
}
