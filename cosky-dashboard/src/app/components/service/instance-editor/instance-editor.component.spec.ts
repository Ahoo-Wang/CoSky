import {ComponentFixture, TestBed} from '@angular/core/testing';

import {InstanceEditorComponent} from './instance-editor.component';

describe('InstanceEditorComponent', () => {
  let component: InstanceEditorComponent;
  let fixture: ComponentFixture<InstanceEditorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ InstanceEditorComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
