import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TextCard } from './text-card';

describe('TextCard', () => {
  let component: TextCard;
  let fixture: ComponentFixture<TextCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TextCard]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TextCard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
