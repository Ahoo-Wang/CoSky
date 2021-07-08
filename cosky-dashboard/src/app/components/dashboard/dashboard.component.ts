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
import {StatClient} from '../../api/stat/StatClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {StatDto} from '../../api/stat/StatDto';
import {Stats} from '../../api/stat/Stats';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  stat: StatDto;

  constructor(private namespaceContext: NamespaceContext,
              private statClient: StatClient) {
    this.stat = Stats.of();
  }

  ngOnInit(): void {
    this.getStat();
    this.namespaceContext.subscribeNamespaceChanged('/dashboard', ns => {
      this.getStat();
    });
  }

  private getStat(): void {
    this.statClient.getStat(this.namespaceContext.ensureCurrentNamespace()).subscribe(stat => {
      this.stat = stat;
    });
  }
}
