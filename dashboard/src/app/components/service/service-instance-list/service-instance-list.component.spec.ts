import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ServiceInstanceListComponent} from './service-instance-list.component';

describe('ServiceInstanceListComponent', () => {
  let component: ServiceInstanceListComponent;
  let fixture: ComponentFixture<ServiceInstanceListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ServiceInstanceListComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ServiceInstanceListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
