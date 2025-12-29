import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AssignFolderDialog } from './assign-folder-dialog';

describe('AssignFolderDialog', () => {
  let component: AssignFolderDialog;
  let fixture: ComponentFixture<AssignFolderDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignFolderDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AssignFolderDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
