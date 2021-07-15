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
import {NamespaceContext} from '../../../core/NamespaceContext';
import {NamespaceClient} from '../../../api/namespace/NamespaceClient';

@Component({
  selector: 'app-namespace-selector',
  templateUrl: './namespace-selector.component.html',
  styleUrls: ['./namespace-selector.component.css']
})
export class NamespaceSelectorComponent implements OnInit {
  currentNamespace: string | null = null;
  namespaces: string[] = [];

  constructor(private namespaceContext: NamespaceContext, private namespaceClient: NamespaceClient) {

  }

  ngOnInit(): void {
    this.currentNamespace = this.namespaceContext.getCurrent();
    this.namespaceClient.getNamespaces().subscribe(namespaces => {
      this.namespaces = namespaces;

      if (namespaces.length === 0) {
        return;
      }

      if (!this.currentNamespace
        || namespaces.indexOf(this.currentNamespace) < 0) {
        this.currentNamespace = namespaces[0];
        this.onNamespaceSelected();
      }
    });
  }

  onNamespaceSelected(): void {
    if (this.currentNamespace) {
      this.namespaceContext.setCurrent(this.currentNamespace);
    }
  }
}
