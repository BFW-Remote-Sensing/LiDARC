import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateFolderDialog } from './create-folder-dialog';

describe('CreateFolderDialog', () => {
  let component: CreateFolderDialog;
  let fixture: ComponentFixture<CreateFolderDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateFolderDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CreateFolderDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
