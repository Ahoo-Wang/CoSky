import {Component, Input, OnInit} from '@angular/core';
import {ServiceInstanceDto} from '../../../api/service/ServiceInstanceDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ServiceClient} from '../../../api/service/ServiceClient';
import {InstanceEditorService} from '../instance-editor.service';

@Component({
  selector: 'app-service-instance-list',
  templateUrl: './service-instance-list.component.html',
  styleUrls: ['./service-instance-list.component.scss']
})
export class ServiceInstanceListComponent implements OnInit {
  @Input() serviceId!: string;
  instances!: ServiceInstanceDto[];

  constructor(private namespaceContext: NamespaceContext,
              private serviceClient: ServiceClient,
              private instanceEditorService: InstanceEditorService) {
  }

  ngOnInit(): void {
    this.getInstances();
  }

  getInstances(): void {
    this.serviceClient.getInstances(this.namespaceContext.ensureCurrentNamespace(), this.serviceId).subscribe(instances => {
      this.instances = instances;
    });
  }


  deregister(instance: ServiceInstanceDto): void {
    this.serviceClient.deregister(this.namespaceContext.ensureCurrentNamespace(), instance.serviceId, instance.instanceId)
      .subscribe(result => {
        this.getInstances();
      });
  }

  openEditInstance(serviceId: string, instance?: ServiceInstanceDto): void {
    this.instanceEditorService.openEditInstance(serviceId, instance);
  }

}
