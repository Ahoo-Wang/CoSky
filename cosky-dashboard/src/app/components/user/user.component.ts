import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {UserClient} from "../../api/user/UserClient";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {ConfigImporterComponent} from "../config/config-importer/config-importer.component";
import {UserEditorComponent} from "./user-editor/user-editor.component";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  users: UserDto[] = [];

  constructor(private userClient: UserClient, private drawerService: NzDrawerService) {
  }

  loadUsers() {
    this.userClient.query().subscribe(resp => {
      this.users = resp;
    })
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  removeUser(user: UserDto) {
    this.userClient.removeUser(user.username).subscribe(resp => {
      this.loadUsers();
    })
  }

  isSystem(user: UserDto) {
    return 'cosky' === user.username;
  }

  openEditor(user: UserDto | null) {
    const title = user ? `Edit User [${user.username}]` : 'Add User';
    const drawerRef = this.drawerService.create<UserEditorComponent, {}, string>({
      nzTitle: title,
      nzWidth: '40%',
      nzContent: UserEditorComponent,
      nzContentParams: {
        user
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterSave.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.loadUsers();
      });
    });
  }
}
