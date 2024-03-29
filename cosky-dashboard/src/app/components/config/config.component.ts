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

import {Component, OnInit} from '@angular/core';
import {ConfigClient} from '../../api/config/ConfigClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {ConfigEditorService} from './config-editor.service';
import {RowExpand} from '../../model/RowExpand';
import {HttpEventType} from "@angular/common/http";
import {SecurityService} from "../../security/SecurityService";

@Component({
  selector: 'app-config',
  templateUrl: './config.component.html',
  styleUrls: ['./config.component.scss']
})
export class ConfigComponent implements OnInit {
  configs!: string[];
  displayConfigs!: RowExpand<string>[];
  searchVisible = false;
  searchValue = '';

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private configEditorService: ConfigEditorService,
              private securityService: SecurityService) {
  }

  ngOnInit(): void {
    this.getConfigs();
    this.namespaceContext.subscribeNamespaceChanged('/config', namespace => {
      this.getConfigs();
    });
  }


  getConfigs(): void {
    this.configClient.getConfigs(this.namespaceContext.ensureCurrentNamespace()).subscribe(configs => {
      this.configs = configs;
      this.displayConfigs = this.asDisplayData(configs);
    });
  }

  private asDisplayData(configs: string[]): RowExpand<string>[] {
    return this.configs.map(config => RowExpand.of(config));
  }

  removeConfig(configId: string): void {
    this.configClient.removeConfig(this.namespaceContext.ensureCurrentNamespace(), configId).subscribe(result => {
      this.getConfigs();
    });
  }

  openEditConfig(configId?: string): void {
    this.configEditorService.openEditConfig({
      configId, afterSet: (result) => {
        this.getConfigs();
      }
    });
  }

  openImportConfig(): void {
    this.configEditorService.openImportConfig((result) => {
      if (result) {
        this.getConfigs();
      }
    });
  }


  search(): void {
    this.searchVisible = false;
    if (this.searchValue.length === 0) {
      this.displayConfigs = this.asDisplayData(this.configs);
      return;
    }
    this.displayConfigs = this.configs.filter((item) => item.indexOf(this.searchValue) !== -1)
      .map(config => RowExpand.of(config));
  }

  reset(): void {
    this.searchValue = '';
    this.search();
  }

  exportConfigs() {
    this.configClient.exportConfigs(this.namespaceContext.ensureCurrentNamespace()).subscribe();
  }

}
