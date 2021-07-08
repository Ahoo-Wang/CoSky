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

import {Injectable} from '@angular/core';
import {NzNotificationService} from 'ng-zorro-antd/notification';
import {ActivatedRoute, Router} from '@angular/router';


const NAMESPACE_KEY = 'cosky:ns:current';
const SYSTEM_NAMESPACE = 'cosky-{system}';
const SYSTEM_NAMESPACES = new Set(['cosky-{default}', SYSTEM_NAMESPACE]);

@Injectable({providedIn: 'root'})
export class NamespaceContext {
  namespaceChangedSubscribers: Map<string, (namespace: string) => void> = new Map<string, (namespace: string) => void>();

  constructor(private notification: NzNotificationService,
              private activatedRoute: ActivatedRoute,
              private router: Router) {
    this.resetIfAbsent();
  }

  private resetIfAbsent(): void {
    if (!this.getCurrent()) {
      this.setCurrent(SYSTEM_NAMESPACE);
    }
  }

  rest(): void {
    this.setCurrent(SYSTEM_NAMESPACE);
  }

  setCurrent(namespace: string): void {
    localStorage.setItem(NAMESPACE_KEY, namespace);
    this.namespaceChangedSubscribers.forEach((subscriber, subscriberName) => {
      if (this.router.url === subscriberName) {
        subscriber(namespace);
      }
    });
  }

  subscribeNamespaceChanged(path: string, subscriber: (namespace: string) => void): void {
    this.namespaceChangedSubscribers.set(path, subscriber);
  }

  getCurrent(): string | null {
    return localStorage.getItem(NAMESPACE_KEY);
  }

  ensureCurrentNamespace(): string {
    const currentNamespace = this.getCurrent();
    if (currentNamespace) {
      return currentNamespace;
    }
    this.notification
      .blank(
        'Namespace Context ERROR',
        'Please Select a namespace.'
      );
    throw new Error('Please Select a namespace.');
  }

  isSystem(namespace: string): boolean {
    return SYSTEM_NAMESPACES.has(namespace);
  }
}
