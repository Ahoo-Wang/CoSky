import {Component, Input, OnInit} from '@angular/core';
import {ConfigVersionDto} from '../../../api/config/ConfigVersionDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {ConfigEditorService} from '../config-editor.service';

@Component({
  selector: 'app-config-version-list',
  templateUrl: './config-version-list.component.html',
  styleUrls: ['./config-version-list.component.scss']
})
export class ConfigVersionListComponent implements OnInit {
  @Input() configId!: string;
  configVersions!: ConfigVersionDto[];

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private configEditorService: ConfigEditorService) {
  }

  ngOnInit(): void {
    this.getConfigVersions();
  }

  getConfigVersions(): void {
    this.configClient.getConfigVersions(this.namespaceContext.ensureCurrentNamespace(), this.configId).subscribe(versions => {
      this.configVersions = versions;

    });
  }

  openConfigVersionView(configId: string, version: number): void {
    this.configEditorService.openConfigVersionView(configId, version, (result => this.getConfigVersions()));
  }
}
