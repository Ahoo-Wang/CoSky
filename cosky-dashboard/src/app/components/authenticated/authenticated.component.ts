import {Component, OnInit} from '@angular/core';
import {SecurityService} from "../../security/SecurityService";
import {TokenPayload} from "../../api/authenticate/TokenPayload";
import {NzDrawerService} from "ng-zorro-antd/drawer";
import {UserChangePwdComponent} from "../user/user-change-pwd/user-change-pwd.component";
import {NzMessageService} from "ng-zorro-antd/message";

@Component({
  selector: 'app-authenticated',
  templateUrl: './authenticated.component.html',
  styleUrls: ['./authenticated.component.scss']
})
export class AuthenticatedComponent implements OnInit {
  title = 'CoSky Dashboard';
  isCollapsed = false;
  currentUser: TokenPayload;

  constructor(private securityService: SecurityService
    , private messageService: NzMessageService
    , private drawerService: NzDrawerService) {
    this.currentUser = this.securityService.getCurrentUser();
  }

  ngOnInit(): void {

  }


  signOut() {
    this.securityService.signOut();
  }


  openChangePwd() {
    const drawerRef = this.drawerService.create<UserChangePwdComponent, {}, string>({
      nzTitle: `Change User:[${this.currentUser.sub}] Password`,
      nzWidth: '30%',
      nzContent: UserChangePwdComponent
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterChange.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        this.messageService.success("Password reset complete!")
      });
    });
  }
}
