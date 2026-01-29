import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StatsScenery } from './stats-scenery';

describe('StatsScenery', () => {
  let component: StatsScenery;
  let fixture: ComponentFixture<StatsScenery>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatsScenery]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StatsScenery);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
