import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {RoleClient} from "../../api/role/RoleClient";

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit {
  roles: string[] = [];

  constructor(private roleClient: RoleClient) {
  }

  loadRoles() {
    this.roleClient.getAllRole().subscribe(resp => {
      this.roles = resp;
    })
  }

  ngOnInit(): void {
    this.loadRoles();
  }

  removeRole(role: string) {
    this.roleClient.removeRole(role).subscribe(resp => {
      this.loadRoles();
    })
  }

  isSystem(role: string) {
    return 'admin' === role;
  }

  showAddRole() {

  }
}
