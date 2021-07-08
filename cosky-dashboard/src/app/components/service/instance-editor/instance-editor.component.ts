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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ServiceInstanceDto} from '../../../api/service/ServiceInstanceDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ServiceClient} from '../../../api/service/ServiceClient';
import {NzMessageService} from 'ng-zorro-antd/message';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-instance-editor',
  templateUrl: './instance-editor.component.html',
  styleUrls: ['./instance-editor.component.scss']
})
export class InstanceEditorComponent implements OnInit {
  validateForm!: FormGroup;
  @Input() instance!: ServiceInstanceDto;
  @Output() afterRegister: EventEmitter<boolean> = new EventEmitter<boolean>();
  metadata!: string;

  constructor(private namespaceContext: NamespaceContext,
              private serviceClient: ServiceClient,
              private messageService: NzMessageService,
              private formBuilder: FormBuilder) {

  }

  ngOnInit(): void {
    if (this.instance) {
      this.metadata = JSON.stringify(this.instance.metadata);
    }

    this.validateForm = this.formBuilder.group({
      serviceId: [this.instance.serviceId],
      instanceId: [this.instance.instanceId],
      schema: [this.instance.schema, [Validators.required]],
      host: [this.instance.host, [Validators.required]],
      port: [this.instance.port, [Validators.required]],
      weight: [this.instance.weight, [Validators.required]],
      ephemeral: [this.instance.ephemeral, [Validators.required]],
      metadata: [this.metadata]
    });
  }


  register(): void {
    try {
      const objMetadata = JSON.parse(this.metadata);
      this.instance.metadata = objMetadata;
    } catch (e) {
      this.messageService.error(`Metadata format error.\n${e}`);
      return;
    }

    this.serviceClient.register(this.namespaceContext.ensureCurrentNamespace(), this.instance.serviceId, this.instance)
      .subscribe(result => {
        this.messageService.success('Instance saved successfull!');
        this.afterRegister.emit(result);
      }, error => {
        this.messageService.error(`Instance saved failed!\n${error}`);
        this.afterRegister.emit(false);
      });
  }
}
