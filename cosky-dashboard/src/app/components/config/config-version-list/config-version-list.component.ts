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

import {Component, Input, OnInit} from '@angular/core';
import {ConfigVersionDto} from '../../../api/config/ConfigVersionDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {ConfigEditorService} from '../config-editor.service';

@Component({
  selector: 'app-config-version-list',
  templateUrl: './config-version-list.component.html',
  styleUrls: ['./config-version-list.component.scss']
})
export class ConfigVersionListComponent implements OnInit {
  @Input() configId!: string;
  configVersions!: ConfigVersionDto[];

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private configEditorService: ConfigEditorService) {
  }

  ngOnInit(): void {
    this.getConfigVersions();
  }

  getConfigVersions(): void {
    this.configClient.getConfigVersions(this.namespaceContext.ensureCurrentNamespace(), this.configId).subscribe(versions => {
      this.configVersions = versions;

    });
  }

  openConfigVersionView(configId: string, version: number): void {
    this.configEditorService.openConfigVersionView(configId, version, (result => this.getConfigVersions()));
  }
}
