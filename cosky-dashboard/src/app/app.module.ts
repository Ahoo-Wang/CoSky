/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
import {BrowserModule} from '@angular/platform-browser';

import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {NZ_I18N, zh_CN} from 'ng-zorro-antd/i18n';
import {registerLocaleData} from '@angular/common';
import zh from '@angular/common/locales/zh';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HTTP_INTERCEPTORS, HttpClientModule} from '@angular/common/http';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {IconsProviderModule} from './icons-provider.module';

import {NamespaceSelectorComponent} from './components/namespace/namespace-selector/namespace-selector.component';
import {NamespaceComponent} from './components/namespace/namespace.component';
import {ConfigComponent} from './components/config/config.component';
import {ServiceComponent} from './components/service/service.component';
import {ConfigEditorComponent} from './components/config/config-editor/config-editor.component';
import {ServiceInstanceListComponent} from './components/service/service-instance-list/service-instance-list.component';
import {InstanceEditorComponent} from './components/service/instance-editor/instance-editor.component';
import {ConfigVersionListComponent} from './components/config/config-version-list/config-version-list.component';
import {ConfigVersionComponent} from './components/config/config-version/config-version.component';
import {DashboardComponent} from './components/dashboard/dashboard.component';
import {ConfigImporterComponent} from './components/config/config-importer/config-importer.component';
import {AuthInterceptor} from "./security/AuthInterceptor";
import {LoginComponent} from './components/login/login.component';
import {UserComponent} from './components/user/user.component';
import {RoleComponent} from './components/role/role.component';
import {RoleEditorComponent} from './components/role/role-editor/role-editor.component';
import {UserEditorComponent} from './components/user/user-editor/user-editor.component';
import {AuthenticatedComponent} from './components/authenticated/authenticated.component';
import {UserChangePwdComponent} from './components/user/user-change-pwd/user-change-pwd.component';
import {UserAddComponent} from './components/user/user-add/user-add.component';
import {AuditLogComponent} from './components/audit-log/audit-log.component';
import {TopologyComponent} from './components/topology/topology.component';
import {NzCodeEditorModule} from 'ng-zorro-antd/code-editor';
import {NgZorroAntdModule} from "./ng-zorro-antd.module";
import {NzWaterMarkModule} from "ng-zorro-antd/water-mark";

registerLocaleData(zh);

export const httpInterceptorProviders = [
  {provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true},
];

@NgModule({
  declarations: [
    AppComponent,
    NamespaceSelectorComponent,
    NamespaceComponent,
    ConfigComponent,
    ServiceComponent,
    ConfigEditorComponent,
    ServiceInstanceListComponent,
    InstanceEditorComponent,
    ConfigVersionListComponent,
    ConfigVersionComponent,
    DashboardComponent,
    ConfigImporterComponent,
    LoginComponent,
    UserComponent,
    RoleComponent,
    RoleEditorComponent,
    UserEditorComponent,
    AuthenticatedComponent,
    UserChangePwdComponent,
    UserAddComponent,
    AuditLogComponent,
    TopologyComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule, ReactiveFormsModule,
    HttpClientModule,
    BrowserAnimationsModule,
    IconsProviderModule,
    NgZorroAntdModule,
    NzWaterMarkModule,
    NzCodeEditorModule
  ],
  providers: [{provide: NZ_I18N, useValue: zh_CN}, httpInterceptorProviders],
  bootstrap: [AppComponent]
})
export class AppModule {
}
