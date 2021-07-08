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
import {ConfigClient} from '../../api/config/ConfigClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {ConfigEditorService} from './config-editor.service';
import {RowExpand} from '../../model/RowExpand';
import {HttpEventType} from "@angular/common/http";

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
  exportUrl = '';

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private configEditorService: ConfigEditorService) {
  }

  ngOnInit(): void {
    this.getConfigs();
    this.getExportUrl();
    this.namespaceContext.subscribeNamespaceChanged('/config', namespace => {
      this.getConfigs();
      this.getExportUrl();
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

  getExportUrl(): string {
    this.exportUrl = this.configClient.getExportUrl(this.namespaceContext.ensureCurrentNamespace());
    return this.exportUrl;
  }

  exportConfigs() {
    this.configClient.exportConfigs(this.namespaceContext.ensureCurrentNamespace()).subscribe(response => {
      if (response.type === HttpEventType.DownloadProgress) {
        console.log("download progress");
      }
      if (response.type === HttpEventType.Response) {
        console.log("download completed");
      }
    });
  }


}
