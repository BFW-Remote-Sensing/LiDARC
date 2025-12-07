import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComparisonDetails } from './comparison-details';

describe('ComparisonDetails', () => {
  let component: ComparisonDetails;
  let fixture: ComponentFixture<ComparisonDetails>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparisonDetails]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ComparisonDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
