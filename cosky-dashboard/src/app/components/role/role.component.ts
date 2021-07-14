import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {RoleClient} from "../../api/role/RoleClient";
import {UserEditorComponent} from "../user/user-editor/user-editor.component";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {RoleEditorComponent} from "./role-editor/role-editor.component";

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit {
  roles: string[] = [];

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

  removeRole(role: string) {
    this.roleClient.removeRole(role).subscribe(resp => {
      this.loadRoles();
    })
  }

  isSystem(role: string) {
    return 'admin' === role;
  }


  openEditor(role: string | null) {
    const title = role ? `Edit Role [${role}]` : 'Add Role';
    const drawerRef = this.drawerService.create<RoleEditorComponent, {}, string>({
      nzTitle: title,
      nzWidth: '40%',
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
