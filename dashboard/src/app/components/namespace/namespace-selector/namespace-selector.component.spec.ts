import {ComponentFixture, fakeAsync, TestBed} from '@angular/core/testing';
import {NamespaceSelectorComponent} from './namespace-selector.component';

describe('NamespaceComponent', () => {
  let component: NamespaceSelectorComponent;
  let fixture: ComponentFixture<NamespaceSelectorComponent>;

  beforeEach(fakeAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ NamespaceSelectorComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NamespaceSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should compile', () => {
    expect(component).toBeTruthy();
  });
});
