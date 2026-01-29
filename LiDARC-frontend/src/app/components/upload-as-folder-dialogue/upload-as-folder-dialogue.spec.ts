import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UploadAsFolderDialogue } from './upload-as-folder-dialogue';

describe('UploadAsFolderDialogue', () => {
  let component: UploadAsFolderDialogue;
  let fixture: ComponentFixture<UploadAsFolderDialogue>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UploadAsFolderDialogue]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UploadAsFolderDialogue);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
