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

import {Component, OnInit} from '@angular/core';
import {NamespaceClient} from '../../api/namespace/NamespaceClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {NzMessageService} from 'ng-zorro-antd/message';


@Component({
  selector: 'app-namespace',
  templateUrl: './namespace.component.html',
  styleUrls: ['./namespace.component.scss']
})
export class NamespaceComponent implements OnInit {
  namespaces: string[] = [];

  constructor(private namespaceContext: NamespaceContext,
              private namespaceClient: NamespaceClient,
              private messageService: NzMessageService) {
  }

  getNamespaces(): void {
    this.namespaceClient.getNamespaces().subscribe(namespaces => {
      this.namespaces = namespaces;
    });
  }


  ngOnInit(): void {
    this.getNamespaces();
  }

  removeNamespace(namespace: string): void {
    this.namespaceClient.removeNamespace(namespace).subscribe(result => {
      this.messageService.success(`Namespace:[${namespace}] removal successful.`);
      this.getNamespaces();
    });
  }

  addNamespace(namespace: string): void {
    this.namespaceClient.setNamespace(namespace).subscribe(() => {
      this.messageService.success(`Namespace:[${namespace}] added successful.`);
      this.getNamespaces();
    });
  }

  isSystem(namespace: string): boolean {
    return this.namespaceContext.isSystem(namespace);
  }
}
