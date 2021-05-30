import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ConfigVersionComponent} from './config-version.component';

describe('ConfigVersionComponent', () => {
  let component: ConfigVersionComponent;
  let fixture: ComponentFixture<ConfigVersionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigVersionComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigVersionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
