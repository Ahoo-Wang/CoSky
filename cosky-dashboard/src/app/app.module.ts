/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';

import {AppRoutingModule} from './app-routing.module';
import {AppComponent} from './app.component';
import {NZ_I18N, zh_CN} from 'ng-zorro-antd/i18n';
import {registerLocaleData} from '@angular/common';
import zh from '@angular/common/locales/zh';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HttpClientModule} from '@angular/common/http';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {IconsProviderModule} from './icons-provider.module';
import {NzLayoutModule} from 'ng-zorro-antd/layout';
import {NzMenuModule} from 'ng-zorro-antd/menu';
import {NzTableModule} from 'ng-zorro-antd/table';
import {NzSelectModule} from 'ng-zorro-antd/select';
import {NzInputModule} from 'ng-zorro-antd/input';
import {NzPopconfirmModule} from 'ng-zorro-antd/popconfirm';
import {NzCardModule} from 'ng-zorro-antd/card';
import {NzDividerModule} from 'ng-zorro-antd/divider';
import {NzButtonModule} from 'ng-zorro-antd/button';
import {NzNotificationModule} from 'ng-zorro-antd/notification';
import {NzDrawerModule} from 'ng-zorro-antd/drawer';
import {NzFormModule} from 'ng-zorro-antd/form';
import {NzInputNumberModule} from 'ng-zorro-antd/input-number';
import {NzSwitchModule} from 'ng-zorro-antd/switch';
import {NzDropDownModule} from 'ng-zorro-antd/dropdown';
import {NzStatisticModule} from 'ng-zorro-antd/statistic';
import {NzUploadModule} from 'ng-zorro-antd/upload';
import {MonacoEditorModule} from 'ngx-monaco-editor';

import {NamespaceSelectorComponent} from './components/namespace/namespace-selector/namespace-selector.component';
import {NamespaceComponent} from './components/namespace/namespace.component';
import {ConfigComponent} from './components/config/config.component';
import {ServiceComponent} from './components/service/service.component';
import {ConfigEditorComponent} from './components/config/config-editor/config-editor.component';
import {ServiceInstanceListComponent} from './components/service/service-instance-list/service-instance-list.component';
import {InstanceEditorComponent} from './components/service/instance-editor/instance-editor.component';
import {NzMessageModule} from 'ng-zorro-antd/message';
import {ConfigVersionListComponent} from './components/config/config-version-list/config-version-list.component';
import {ConfigVersionComponent} from './components/config/config-version/config-version.component';
import {DashboardComponent} from './components/dashboard/dashboard.component';
import {ConfigImporterComponent} from './components/config/config-importer/config-importer.component';

registerLocaleData(zh);

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
    ConfigImporterComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule, ReactiveFormsModule,
    HttpClientModule,
    BrowserAnimationsModule,
    IconsProviderModule,
    NzLayoutModule,
    NzMenuModule,
    NzTableModule,
    NzSelectModule,
    NzInputModule,
    NzPopconfirmModule,
    NzCardModule,
    NzDividerModule,
    NzButtonModule,
    NzNotificationModule,
    NzMessageModule,
    NzDrawerModule,
    NzFormModule,
    NzInputNumberModule,
    NzSwitchModule,
    NzDropDownModule,
    NzStatisticModule,
    NzUploadModule,
    MonacoEditorModule,
    MonacoEditorModule.forRoot()
  ],
  providers: [{provide: NZ_I18N, useValue: zh_CN}],
  bootstrap: [AppComponent]
})
export class AppModule {
}
