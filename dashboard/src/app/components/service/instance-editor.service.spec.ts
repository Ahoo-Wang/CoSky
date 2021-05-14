import {TestBed} from '@angular/core/testing';

import {InstanceEditorService} from './instance-editor.service';

describe('InstanceEditorService', () => {
  let service: InstanceEditorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(InstanceEditorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
