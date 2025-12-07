import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FileDetailsCard } from './file-details-card';

describe('FileDetailsCard', () => {
  let component: FileDetailsCard;
  let fixture: ComponentFixture<FileDetailsCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FileDetailsCard]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FileDetailsCard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
