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
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {ConfigDto} from '../../../api/config/ConfigDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {NzMessageService} from 'ng-zorro-antd/message';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {ConfigName, Configs} from '../../../api/config/Configs';


@Component({
  selector: 'app-config-editor',
  templateUrl: './config-editor.component.html',
  styleUrls: ['./config-editor.component.scss']
})
export class ConfigEditorComponent implements OnInit {
  validateForm!: FormGroup;
  @Input() configId?: string;
  @Output() afterSet: EventEmitter<boolean> = new EventEmitter<boolean>();
  config!: ConfigDto;
  isAdd = true;
  editorOptions = {theme: 'vs-dark', language: 'yaml'};
  configName!: ConfigName;

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private messageService: NzMessageService,
              private formBuilder: FormBuilder) {

  }

  ngOnInit(): void {
    this.config = Configs.of();
    this.configName = ConfigName.of('.yaml');

    if (this.configId) {
      this.isAdd = false;
      this.configClient.getConfig(this.namespaceContext.ensureCurrentNamespace(), this.configId)
        .subscribe(config => {
          this.config = config;
          this.configName = ConfigName.of(config.configId);
          this.onExtChanged(this.configName.ext);
        });
    }

    const controlsConfig = {
      configName: [this.configName.name, [Validators.required]],
      version: [this.config.version],
      hash: [this.config.hash],
      data: [this.config.data],
      createTime: [this.config.createTime]
    };
    if (!this.isAdd) {
      controlsConfig.configName = [this.configName.name];
    }

    this.validateForm = this.formBuilder.group(controlsConfig);
  }

  setConfig(): void {
    this.config.configId = this.configName.toId();
    this.configClient.setConfig(this.namespaceContext.ensureCurrentNamespace(), this.config.configId, this.config.data)
      .subscribe(result => {
        this.messageService.success(`Config[${this.config.configId}] added successfull!`);
        this.afterSet.emit(result);
      }, error => {
        this.messageService.error(`Config[${this.config.configId}] add failed!\n${error}`);
        this.afterSet.emit(false);
      });
  }

  onExtChanged(configExt: string): void {
    this.configName.ext = configExt;
    const language = Configs.extAsLang(configExt);
    this.editorOptions = Object.assign({}, this.editorOptions, {language});
  }
}
