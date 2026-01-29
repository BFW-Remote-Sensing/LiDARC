import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ComparableItems } from './comparable-items';

describe('ComparableItems', () => {
  let component: ComparableItems;
  let fixture: ComponentFixture<ComparableItems>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparableItems]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ComparableItems);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
