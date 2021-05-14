import {Component, OnInit} from '@angular/core';
import {NamespaceClient} from '../../api/namespace/NamespaceClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {NzMessageService} from 'ng-zorro-antd/message';


@Component({
  selector: 'app-namespace',
  templateUrl: './namespace.component.html',
  styleUrls: ['./namespace.component.scss']
})
export class NamespaceComponent implements OnInit {
  namespaces: string[] = [];

  constructor(private namespaceContext: NamespaceContext,
              private namespaceClient: NamespaceClient,
              private messageService: NzMessageService) {
  }

  getNamespaces(): void {
    this.namespaceClient.getNamespaces().subscribe(namespaces => {
      this.namespaces = namespaces;
    });
  }


  ngOnInit(): void {
    this.getNamespaces();
  }

  removeNamespace(namespace: string): void {
    this.namespaceClient.removeNamespace(namespace).subscribe(result => {
      this.messageService.success(`Namespace:[${namespace}] removal successful.`);
      this.getNamespaces();
    });
  }

  addNamespace(namespace: string): void {
    this.namespaceClient.setNamespace(namespace).subscribe(() => {
      this.messageService.success(`Namespace:[${namespace}] added successful.`);
      this.getNamespaces();
    });
  }

  isSystem(namespace: string): boolean {
    return this.namespaceContext.isSystem(namespace);
  }
}
