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

import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {ConfigClient, ImportPolicy} from '../../../api/config/ConfigClient';
import {NzUploadChangeParam, NzUploadFile} from 'ng-zorro-antd/upload';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {NzMessageService} from 'ng-zorro-antd/message';


@Component({
  selector: 'app-config-importer',
  templateUrl: './config-importer.component.html',
  styleUrls: ['./config-importer.component.scss']
})
export class ConfigImporterComponent implements OnInit {
  @Output() afterImport: EventEmitter<boolean> = new EventEmitter<boolean>();
  importPolicy: ImportPolicy = 'skip';

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private messageService: NzMessageService) {
  }

  ngOnInit(): void {
  }

  getImportData = (file: NzUploadFile) => {
    return {
      policy: this.importPolicy
    };
  };

  getImportUrl(): string {
    return this.configClient.getImportUrl(this.namespaceContext.ensureCurrentNamespace());
  }

  handleChange({file, fileList}: NzUploadChangeParam): void {
    const status = file.status;
    if (status !== 'uploading') {
      console.log(file, fileList);
    }
    if (status === 'done') {
      this.afterImport.emit(true);
      this.messageService.success(`${file.name} file uploaded successfully.`);
    } else if (status === 'error') {
      this.afterImport.emit(false);
      this.messageService.error(`${file.name} file upload failed.`);
    }
  }

}
