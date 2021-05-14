import {Component, OnInit} from '@angular/core';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {NamespaceClient} from '../../../api/namespace/NamespaceClient';

@Component({
  selector: 'app-namespace-selector',
  templateUrl: './namespace-selector.component.html',
  styleUrls: ['./namespace-selector.component.css']
})
export class NamespaceSelectorComponent implements OnInit {
  currentNamespace: string | null = null;
  namespaces: string[] = [];

  constructor(private namespaceContext: NamespaceContext, private namespaceClient: NamespaceClient) {

  }

  ngOnInit(): void {
    this.currentNamespace = this.namespaceContext.getCurrent();
    this.namespaceClient.getNamespaces().subscribe(namespaces => {
      this.namespaces = namespaces;
      if (!this.currentNamespace && namespaces.length > 0) {
        this.currentNamespace = namespaces[0];
        this.onNamespaceSelected();
      }
    });
  }

  onNamespaceSelected(): void {
    if (this.currentNamespace) {
      this.namespaceContext.setCurrent(this.currentNamespace);
    }
  }
}
