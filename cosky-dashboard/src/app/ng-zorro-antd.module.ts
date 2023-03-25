import { NgModule } from '@angular/core';

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
import {NzMessageModule} from 'ng-zorro-antd/message';

@NgModule({
  exports: [
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
    NzUploadModule
  ]
})
export class NgZorroAntdModule {

}
