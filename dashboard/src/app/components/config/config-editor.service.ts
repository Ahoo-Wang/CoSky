import {Injectable} from '@angular/core';
import {NzDrawerService} from 'ng-zorro-antd/drawer';
import {ConfigEditorComponent} from './config-editor/config-editor.component';
import {ConfigVersionComponent} from './config-version/config-version.component';

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
      nzWidth: '40%',
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

  openConfigVersionView(configId: string, version: number, rollbackAfter: (result: boolean) => void): void {

    const drawerRef = this.drawerService.create<ConfigVersionComponent, { configId: string, version: number }, string>({
      nzTitle: `Config [${configId}] Version [${version}]`,
      nzWidth: '40%',
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
