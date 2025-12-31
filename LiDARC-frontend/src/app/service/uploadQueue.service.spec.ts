import {TestBed} from '@angular/core/testing';

import {UploadQueueService} from './uploadQueue.service';

describe('UploadQueueService', () => {
  let service: UploadQueueService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(UploadQueueService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
