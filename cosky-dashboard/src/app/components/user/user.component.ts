import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {UserClient} from "../../api/user/UserClient";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {UserEditorComponent} from "./user-editor/user-editor.component";
import {UserAddComponent} from "./user-add/user-add.component";
import {Clone} from "../../util/Clone";
import {NzMessageService} from "ng-zorro-antd/message";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  users: UserDto[] = [];

  constructor(private userClient: UserClient, private drawerService: NzDrawerService, private messageService: NzMessageService) {
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

  unlock(user: UserDto) {
    this.userClient.unlock(user.username).subscribe(resp => {
      this.messageService.success(`user:${user.username} unlock success!`)
    })
  }

  isSystem(user: UserDto) {
    return 'cosky' === user.username;
  }

  openAdd() {

    const drawerRef = this.drawerService.create<UserAddComponent, {}, string>({
      nzTitle: "Add User",
      nzWidth: '40%',
      nzContent: UserAddComponent
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterAdd.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.loadUsers();
      });
    });
  }

  openEditor(user: UserDto) {
    const drawerRef = this.drawerService.create<UserEditorComponent, {}, string>({
      nzTitle: `Edit User [${user.username}] Role`,
      nzWidth: '30%',
      nzContent: UserEditorComponent,
      nzContentParams: {
        user: Clone.deep(user)
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