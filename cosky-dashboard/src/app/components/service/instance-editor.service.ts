import {Injectable} from '@angular/core';
import {ServiceInstanceDto} from '../../api/service/ServiceInstanceDto';
import {Instances} from '../../api/service/Instances';
import {InstanceEditorComponent} from './instance-editor/instance-editor.component';
import {NzDrawerService} from 'ng-zorro-antd/drawer';

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
      editInstance.ephemeral = true;
    } else {
      editInstance = JSON.parse(JSON.stringify(instance));
    }

    const drawerRef = this.drawerService.create<InstanceEditorComponent, { instance: ServiceInstanceDto }, string>({
      nzTitle: `Managing service[${serviceId}] instances`,
      nzWidth: '550px',
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
