import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {ConfigDto} from '../../../api/config/ConfigDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {NzMessageService} from 'ng-zorro-antd/message';
import {ConfigClient} from '../../../api/config/ConfigClient';
import {Configs} from '../../../api/config/Configs';

@Component({
  selector: 'app-config-editor',
  templateUrl: './config-editor.component.html',
  styleUrls: ['./config-editor.component.scss']
})
export class ConfigEditorComponent implements OnInit {
  validateForm!: FormGroup;
  @Input() configId?: string;
  @Output() afterSet: EventEmitter<boolean> = new EventEmitter<boolean>();
  config: ConfigDto;
  isAdd = true;

  constructor(private namespaceContext: NamespaceContext,
              private configClient: ConfigClient,
              private messageService: NzMessageService,
              private formBuilder: FormBuilder) {
    this.config = Configs.of();
  }

  ngOnInit(): void {
    if (this.configId) {
      this.isAdd = false;
      this.configClient.getConfig(this.namespaceContext.ensureCurrentNamespace(), this.configId)
        .subscribe(config => {
          this.config = config;
        });
    }

    const controlsConfig = {
      configId: [this.config.configId, [Validators.required]],
      version: [this.config.version],
      hash: [this.config.hash],
      data: [this.config.data, [Validators.required]],
      createTime: [this.config.createTime]
    };
    if (!this.isAdd) {
      controlsConfig.configId = [this.config.configId];
    }

    this.validateForm = this.formBuilder.group(controlsConfig);
  }

  setConfig(): void {
    this.configClient.setConfig(this.namespaceContext.ensureCurrentNamespace(), this.config.configId, this.config.data)
      .subscribe(result => {
        this.messageService.success(`Config[${this.config.configId}] added successfull!`);
        this.afterSet.emit(result);
      }, error => {
        this.messageService.error(`Config[${this.config.configId}] add failed!\n${error}`);
        this.afterSet.emit(false);
      });
  }

}
