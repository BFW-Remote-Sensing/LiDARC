import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FolderDetails } from './folder-details';

describe('FolderDetails', () => {
  let component: FolderDetails;
  let fixture: ComponentFixture<FolderDetails>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FolderDetails]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FolderDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
