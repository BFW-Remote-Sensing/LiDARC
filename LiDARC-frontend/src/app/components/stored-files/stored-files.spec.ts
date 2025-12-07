import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StoredFiles } from './stored-files';

describe('StoredFiles', () => {
  let component: StoredFiles;
  let fixture: ComponentFixture<StoredFiles>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StoredFiles]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StoredFiles);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
