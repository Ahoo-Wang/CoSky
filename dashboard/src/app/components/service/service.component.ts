import {Component, OnInit} from '@angular/core';
import {ServiceStatDto} from '../../api/service/ServiceStatDto';
import {ServiceClient} from '../../api/service/ServiceClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {ServiceInstanceDto} from '../../api/service/ServiceInstanceDto';
import {InstanceEditorService} from './instance-editor.service';
import {NzMessageService} from 'ng-zorro-antd/message';
import {RowExpand} from '../../model/RowExpand';

@Component({
  selector: 'app-service',
  templateUrl: './service.component.html',
  styleUrls: ['./service.component.scss']
})
export class ServiceComponent implements OnInit {
  services: ServiceStatDto[] = [];
  displayServices!: RowExpand<ServiceStatDto>[];
  searchVisible = false;
  searchValue = '';

  constructor(private namespaceContext: NamespaceContext,
              private serviceClient: ServiceClient,
              private messageService: NzMessageService,
              private instanceEditorService: InstanceEditorService) {

  }

  ngOnInit(): void {
    this.getServices();
    this.namespaceContext.subscribeNamespaceChanged('/service', namespace => {
      this.getServices();
    });
  }

  private asDisplayData(sourceData: ServiceStatDto[]): RowExpand<ServiceStatDto>[] {
    return sourceData.map(source => {
      return RowExpand.of(source);
    });
  }

  getServices(): void {
    this.serviceClient.getServiceStats(this.namespaceContext.ensureCurrentNamespace()).subscribe(services => {
      this.services = services;
      this.displayServices = this.asDisplayData(services);
    });
  }

  search(): void {
    this.searchVisible = false;
    if (this.searchValue.length === 0) {
      this.displayServices = this.asDisplayData(this.services);
      return;
    }
    this.displayServices = this.services.filter((item) => item.serviceId.indexOf(this.searchValue) !== -1)
      .map(item => RowExpand.of(item));
  }

  reset(): void {
    this.searchValue = '';
    this.search();
  }

  addService(serviceId: string): void {
    this.serviceClient.addService(this.namespaceContext.ensureCurrentNamespace(), serviceId)
      .subscribe(result => {
        if (result) {
          this.messageService.success(`The service[${serviceId}] was added successfully`);
        }
        this.getServices();
      });
  }

  removeService(serviceId: string): void {
    this.serviceClient.removeService(this.namespaceContext.ensureCurrentNamespace(), serviceId)
      .subscribe(result => {
        if (result) {
          this.messageService.success(`The service[${serviceId}] was deleted successfully`);
        }
        this.getServices();
      });
  }

  openEditInstance(serviceId: string, instance?: ServiceInstanceDto): void {
    this.instanceEditorService.openEditInstance(serviceId, instance, result => this.getServices());
  }
}
