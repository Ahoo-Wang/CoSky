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
  searchServiceId = '';
  instanceSortOrder = 'ascend';

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
    if (this.searchServiceId.length === 0) {
      this.displayServices = this.asDisplayData(this.services);
      return;
    }
    this.displayServices = this.services.filter((item) => item.serviceId.indexOf(this.searchServiceId) !== -1)
      .map(item => RowExpand.of(item));
  }

  reset(): void {
    this.searchServiceId = '';
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

  compareInstanceCount(left: RowExpand<ServiceStatDto>, right: RowExpand<ServiceStatDto>) {
    return left.data.instanceCount - right.data.instanceCount;
  }
}
