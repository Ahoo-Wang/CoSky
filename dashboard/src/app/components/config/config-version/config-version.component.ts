import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ConfigHistoryDto} from '../../../api/config/ConfigHistoryDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {Configs} from '../../../api/config/Configs';

@Component({
  selector: 'app-config-version',
  templateUrl: './config-version.component.html',
  styleUrls: ['./config-version.component.scss']
})
export class ConfigVersionComponent implements OnInit {
  @Input() configId!: string;
  @Input() version!: number;
  configHistory!: ConfigHistoryDto;
  @Output() rollbackAfter: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient) {
    this.configHistory = Configs.ofHistory();
  }

  ngOnInit(): void {
    this.configClient.getConfigHistory(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(configHistory => {
        this.configHistory = configHistory;
      });
  }

  rollback(): void {
    this.configClient.rollback(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(result => {
        this.rollbackAfter.emit(result);
      });
  }

}
