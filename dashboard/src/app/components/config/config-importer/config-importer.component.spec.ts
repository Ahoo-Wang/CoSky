import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ConfigImporterComponent} from './config-importer.component';

describe('ConfigImporterComponent', () => {
  let component: ConfigImporterComponent;
  let fixture: ComponentFixture<ConfigImporterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ConfigImporterComponent]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigImporterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
