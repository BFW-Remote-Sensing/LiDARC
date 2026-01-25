import {ComponentFixture, TestBed} from '@angular/core/testing';

import {PointFilterDialogue} from './point-filter-dialogue';

describe('PointFilterDialogue', () => {
  let component: PointFilterDialogue;
  let fixture: ComponentFixture<PointFilterDialogue>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PointFilterDialogue]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PointFilterDialogue);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

