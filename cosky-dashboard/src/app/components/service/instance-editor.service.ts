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

import {Injectable} from '@angular/core';
import {ServiceInstanceDto} from '../../api/service/ServiceInstanceDto';
import {Instances} from '../../api/service/Instances';
import {InstanceEditorComponent} from './instance-editor/instance-editor.component';
import {NzDrawerService} from 'ng-zorro-antd/drawer';
import {Clone} from "../../util/Clone";

@Injectable({
  providedIn: 'root'
})
export class InstanceEditorService {

  constructor(private drawerService: NzDrawerService) {
  }


  openEditInstance(serviceId: string, instance?: ServiceInstanceDto, afterRegister?: (result: boolean) => void): void {
    let editInstance = instance;
    if (!editInstance) {
      editInstance = Instances.of();
      editInstance.schema = 'http';
      editInstance.serviceId = serviceId;
      editInstance.isEphemeral = true;
    } else {
      editInstance = Clone.deep(instance);
    }

    const drawerRef = this.drawerService.create<InstanceEditorComponent, { instance: ServiceInstanceDto }, string>({
      nzTitle: `Managing service[${serviceId}] instances`,
      nzWidth: '60vw',
      nzContent: InstanceEditorComponent,
      nzContentParams: {
        instance: editInstance
      }
    });
    drawerRef.afterOpen.subscribe(() => {
      drawerRef.getContentComponent()?.afterRegister.subscribe(result => {
        drawerRef.close('Operation successful');
        if (afterRegister) {
          afterRegister(result);
        }
      });
    });
  }
}
