import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChunkingSettings } from './chunking-settings';

describe('ChunkingSettings', () => {
  let component: ChunkingSettings;
  let fixture: ComponentFixture<ChunkingSettings>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChunkingSettings]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ChunkingSettings);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
