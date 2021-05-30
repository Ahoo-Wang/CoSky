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
