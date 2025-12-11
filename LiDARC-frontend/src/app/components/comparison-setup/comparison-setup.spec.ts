import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComparisonSetup } from './comparison-setup';

describe('ComparisonSetup', () => {
  let component: ComparisonSetup;
  let fixture: ComponentFixture<ComparisonSetup>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparisonSetup]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ComparisonSetup);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
