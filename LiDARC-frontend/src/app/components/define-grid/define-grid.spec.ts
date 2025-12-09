import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DefineGrid } from './define-grid';

describe('DefineGrid', () => {
  let component: DefineGrid;
  let fixture: ComponentFixture<DefineGrid>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DefineGrid]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DefineGrid);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
