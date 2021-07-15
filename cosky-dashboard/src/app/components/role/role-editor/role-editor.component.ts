import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {ResourceActionDto} from "../../../api/role/ResourceActionDto";
import {NamespaceClient} from "../../../api/namespace/NamespaceClient";
import {RoleClient} from "../../../api/role/RoleClient";
import {RoleDto} from "../../../api/role/RoleDto";
import {FormBuilder, FormGroup, Validators} from "@angular/forms";

@Component({
  selector: 'app-role-editor',
  templateUrl: './role-editor.component.html',
  styleUrls: ['./role-editor.component.scss']
})
export class RoleEditorComponent implements OnInit {
  @Input() role!: RoleDto | null;
  @Output() afterSave: EventEmitter<boolean> = new EventEmitter<boolean>();

  resourceActionBind: ResourceActionDto[] = [];
  namespaces: string[] = [];
  roleName!: string;
  desc!: string;
  editorForm!: FormGroup;
  isAdd!: boolean;

  constructor(private namespaceClient: NamespaceClient, private roleClient: RoleClient,
              private formBuilder: FormBuilder) {

  }

  loadRole() {
    if (!this.role) {
      this.isAdd = true;
      return;
    }
    this.isAdd = false;
    this.roleName = this.role.name;
    this.desc = this.role.desc;
    this.roleClient.getResourceBind(this.roleName).subscribe(resp => {
      this.resourceActionBind = resp;
    })
  }

  ngOnInit(): void {
    this.loadRole();
    const controlsConfig = {
      roleName: [this.roleName, [Validators.required]],
      desc: [this.desc, [Validators.required]]
    };
    if (!this.isAdd) {
      controlsConfig.roleName = [this.roleName];
    }
    this.editorForm = this.formBuilder.group(controlsConfig);
    this.namespaceClient.getNamespaces().subscribe(resp => this.namespaces = resp);
  }


  addResourceAction() {
    this.resourceActionBind = [...this.resourceActionBind, {namespace: '', action: 'r'}]
  }

  removeResourceAction(resourceAction: ResourceActionDto) {
    this.resourceActionBind = this.resourceActionBind.filter(resource => resource != resourceAction);
  }

  saveRole() {
    this.roleClient.saveRole(this.roleName, this.desc, this.resourceActionBind).subscribe(resp => {
      this.afterSave.emit(true)
    })
  }
}
