import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReportCreationDialogComponent } from './report-creation-dialog-component';

describe('ReportCreationDialogComponent', () => {
  let component: ReportCreationDialogComponent;
  let fixture: ComponentFixture<ReportCreationDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportCreationDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReportCreationDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
