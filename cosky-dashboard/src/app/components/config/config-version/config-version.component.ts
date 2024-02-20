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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ConfigHistoryDto} from '../../../api/config/ConfigHistoryDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {ConfigName, Configs} from '../../../api/config/Configs';
import {ConfigDto} from '../../../api/config/ConfigDto';

@Component({
  selector: 'app-config-version',
  templateUrl: './config-version.component.html',
  styleUrls: ['./config-version.component.scss']
})
export class ConfigVersionComponent implements OnInit {
  @Input() configId!: string;
  @Input() version!: number;
  configHistory!: ConfigHistoryDto;
  configCurrent!: ConfigDto;
  configHistoryCode: string = '';
  configCurrentCode: string = '';
  lang!: string;
  @Output() rollbackAfter: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient) {
    this.configHistory = Configs.ofHistory();
  }

  ngOnInit(): void {
    const configName = ConfigName.of(this.configId);
    this.lang = Configs.extAsLang(configName.ext);
    this.configClient.getConfigHistory(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(configHistory => {
        this.configHistory = configHistory;
        this.configHistoryCode = configHistory.data
      });
    this.configClient.getConfig(this.namespaceContext.ensureCurrentNamespace(), this.configId)
      .subscribe(config => {
        this.configCurrent = config;
        this.configCurrentCode = config.data;
      });
  }

  rollback(): void {
    this.configClient.rollback(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(result => {
        this.rollbackAfter.emit(result);
      });
  }
}
