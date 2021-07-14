/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {NamespaceComponent} from './components/namespace/namespace.component';
import {ConfigComponent} from './components/config/config.component';
import {ServiceComponent} from './components/service/service.component';
import {DashboardComponent} from './components/dashboard/dashboard.component';
import {UserComponent} from "./components/user/user.component";
import {AuthGuard} from "./security/AuthGuard";
import {RoleComponent} from "./components/role/role.component";
import {LoginComponent} from "./components/login/login.component";

const routes: Routes = [

  {path: '', pathMatch: 'full', redirectTo: '/dashboard'},
  {path: 'login', component: LoginComponent},
  {path: 'dashboard', canActivate: [AuthGuard],component: DashboardComponent},
  {path: 'namespace', canActivate: [AuthGuard],component: NamespaceComponent},
  {path: 'config', canActivate: [AuthGuard], component: ConfigComponent},
  {path: 'service', canActivate: [AuthGuard], component: ServiceComponent},
  {path: 'user', canActivate: [AuthGuard], component: UserComponent},
  {path: 'role', canActivate: [AuthGuard], component: RoleComponent}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
