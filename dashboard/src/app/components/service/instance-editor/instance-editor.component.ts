import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ServiceInstanceDto} from '../../../api/service/ServiceInstanceDto';
import {NamespaceContext} from '../../../core/NamespaceContext';
import {ServiceClient} from '../../../api/service/ServiceClient';
import {NzMessageService} from 'ng-zorro-antd/message';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-instance-editor',
  templateUrl: './instance-editor.component.html',
  styleUrls: ['./instance-editor.component.scss']
})
export class InstanceEditorComponent implements OnInit {
  validateForm!: FormGroup;
  @Input() instance!: ServiceInstanceDto;
  @Output() afterRegister: EventEmitter<boolean> = new EventEmitter<boolean>();

  constructor(private namespaceContext: NamespaceContext,
              private serviceClient: ServiceClient,
              private messageService: NzMessageService,
              private formBuilder: FormBuilder) {

  }

  ngOnInit(): void {
    this.validateForm = this.formBuilder.group({
      serviceId: [this.instance.serviceId],
      instanceId: [this.instance.instanceId],
      schema: [this.instance.schema, [Validators.required]],
      ip: [this.instance.ip, [Validators.required]],
      port: [this.instance.port, [Validators.required]],
      weight: [this.instance.weight, [Validators.required]],
      ephemeral: [this.instance.ephemeral, [Validators.required]]
    });
  }


  register(): void {
    this.serviceClient.register(this.namespaceContext.ensureCurrentNamespace(), this.instance.serviceId, this.instance)
      .subscribe(result => {
        this.messageService.success('Instance added successfull!');
        this.afterRegister.emit(result);
      }, error => {
        this.messageService.error(`Instance add failed!\n${error}`);
        this.afterRegister.emit(false);
      });
  }
}
