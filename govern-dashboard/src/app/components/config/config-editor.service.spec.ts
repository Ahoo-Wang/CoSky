import {TestBed} from '@angular/core/testing';

import {ConfigEditorService} from './config-editor.service';

describe('ConfigEditorService', () => {
  let service: ConfigEditorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ConfigEditorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
