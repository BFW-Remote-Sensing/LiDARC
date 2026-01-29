import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PdfThumbnail } from './pdf-thumbnail';

describe('PdfThumbnail', () => {
  let component: PdfThumbnail;
  let fixture: ComponentFixture<PdfThumbnail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PdfThumbnail]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PdfThumbnail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
