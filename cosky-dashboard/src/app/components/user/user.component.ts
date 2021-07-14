import {Component, OnInit} from '@angular/core';
import {UserDto} from "../../api/user/UserDto";
import {UserClient} from "../../api/user/UserClient";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  users: UserDto[] = [];

  constructor(private userClient: UserClient) {
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

  showAddUser() {

  }
}
