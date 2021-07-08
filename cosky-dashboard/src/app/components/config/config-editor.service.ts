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

import {Injectable} from '@angular/core';
import {NzDrawerService} from 'ng-zorro-antd/drawer';
import {ConfigEditorComponent} from './config-editor/config-editor.component';
import {ConfigVersionComponent} from './config-version/config-version.component';
import {ConfigImporterComponent} from './config-importer/config-importer.component';

interface OpenEditConfigParams {
  configId?: string;
  afterSet?: (result: boolean) => void;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigEditorService {

  constructor(private drawerService: NzDrawerService) {
  }

  openEditConfig({configId, afterSet}: OpenEditConfigParams): void {
    let editorTitle = 'Add config';
    if (configId) {
      editorTitle = `Edit config[${configId}]`;
    }
    const drawerRef = this.drawerService.create<ConfigEditorComponent, { configId?: string }, string>({
      nzTitle: editorTitle,
      nzWidth: '880px',
      nzContent: ConfigEditorComponent,
      nzContentParams: {
        configId
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterSet.subscribe(result => {
        drawerRef.close('Operation successful');
        if (afterSet) {
          afterSet(result);
        }
      });
    });
  }

  openImportConfig(afterImport: (result: boolean) => void): void {
    const editorTitle = 'Import config';

    const drawerRef = this.drawerService.create<ConfigImporterComponent, {}, string>({
      nzTitle: editorTitle,
      nzWidth: '40%',
      nzContent: ConfigImporterComponent,
      nzContentParams: {}
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterImport.subscribe(result => {
        if (result) {
          drawerRef.close('Operation successful');
        }
        if (afterImport) {
          afterImport(result);
        }
      });
    });
  }

  openConfigVersionView(configId: string, version: number, rollbackAfter: (result: boolean) => void): void {

    const drawerRef = this.drawerService.create<ConfigVersionComponent, { configId: string, version: number }, string>({
      nzTitle: `Config [${configId}] Version [${version}]`,
      nzWidth: '880px',
      nzContent: ConfigVersionComponent,
      nzContentParams: {
        configId,
        version
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.rollbackAfter.subscribe(result => {
        drawerRef.close('Operation successful');
        if (rollbackAfter) {
          rollbackAfter(result);
        }
      });
    });
  }
}
