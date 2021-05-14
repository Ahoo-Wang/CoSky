import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ConfigVersionListComponent} from './config-version-list.component';

describe('ConfigVersionListComponent', () => {
  let component: ConfigVersionListComponent;
  let fixture: ComponentFixture<ConfigVersionListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigVersionListComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigVersionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
