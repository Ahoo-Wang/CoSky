import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ConfigHistoryDto} from '../../../api/config/ConfigHistoryDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {ConfigName, Configs} from '../../../api/config/Configs';
import {ConfigDto} from '../../../api/config/ConfigDto';

@Component({
  selector: 'app-config-version',
  templateUrl: './config-version.component.html',
  styleUrls: ['./config-version.component.scss']
})
export class ConfigVersionComponent implements OnInit {
  @Input() configId!: string;
  @Input() version!: number;
  configHistory!: ConfigHistoryDto;
  configCurrent!: ConfigDto;
  @Output() rollbackAfter: EventEmitter<boolean> = new EventEmitter<boolean>();
  configHistoryCode: any = {code: ''};
  configCurrentCode: any = {code: ''};

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient) {
    this.configHistory = Configs.ofHistory();
  }

  ngOnInit(): void {
    const configName = ConfigName.of(this.configId);
    const lang = Configs.extAsLang(configName.ext);
    this.configClient.getConfigHistory(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(configHistory => {
        this.configHistory = configHistory;
        this.configHistoryCode = Object.assign({}, this.configHistoryCode, {language: lang, code: configHistory.data});
      });
    this.configClient.getConfig(this.namespaceContext.ensureCurrentNamespace(), this.configId)
      .subscribe(config => {
        this.configCurrent = config;
        this.configCurrentCode = Object.assign({}, this.configCurrentCode, {language: lang, code: config.data});
      });
  }

  rollback(): void {
    this.configClient.rollback(this.namespaceContext.ensureCurrentNamespace(), this.configId, this.version)
      .subscribe(result => {
        this.rollbackAfter.emit(result);
      });
  }

  onInitDiffEditor($event: any): void {
    console.log($event);
  }
}
