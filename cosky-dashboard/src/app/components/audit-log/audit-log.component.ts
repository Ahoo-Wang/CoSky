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
import {AuditLogClient} from "../../api/audit/AuditLogClient";
import {AuditLogDto} from "../../api/audit/AuditLogDto";
import {NzTableQueryParams} from "ng-zorro-antd/table";

@Component({
  selector: 'app-audit-log',
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.scss']
})
export class AuditLogComponent implements OnInit {
  total: number = 0;
  logs: AuditLogDto[] = [];
  pageIndex: number = 1;
  pageSize: number = 10;
  loading: boolean = true;

  constructor(private auditLogClient: AuditLogClient) {
  }

  ngOnInit(): void {
    this.loadLog();
  }

  loadLog() {
    this.loading = true;
    let offset = (this.pageIndex - 1) * this.pageSize;
    this.auditLogClient.queryLog(offset, this.pageSize).subscribe(resp => {
      this.loading = false;
      this.total = resp.total;
      this.logs = resp.list;
    })
  }

  onQueryParamsChange(params: NzTableQueryParams) {
    const {pageSize, pageIndex, sort, filter} = params;
    this.pageSize = params.pageSize;
    this.pageIndex = params.pageIndex;
    this.loadLog();
  }
}
