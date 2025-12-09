import { Component, inject, Input, OnInit, signal, WritableSignal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MetadataService } from '../../service/metadata.service';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatExpansionModule } from '@angular/material/expansion';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import { TextCard } from '../text-card/text-card';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { FormatService } from '../../service/format.service';

@Component({
  selector: 'app-file-details-card',
  imports: [
    CommonModule,
    MatCardModule,
    MatListModule,
    MatChipsModule,
    MatGridListModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatIcon,
    TextCard,
    FormatBytesPipe
  ],
  templateUrl: './file-details-card.html',
  styleUrl: './file-details-card.scss',
})
export class FileDetailsCard implements OnInit {
  @Input() index: string = '';
  @Input() metadataId: number | null = null;
  private metadataService = inject(MetadataService);
  @Input() metadata: FileMetadataDTO | null = null;
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  constructor(private formatService: FormatService) { }

  ngOnInit(): void {
    if (this.metadataId) {
      this.metadataService.getMetadataById(+this.metadataId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (data) => {
            this.metadata = this.formatService.formatMetadata(data);
          },
          error: (error) => {
            console.error('Error fetching metadata by ID:', error);
            this.errorMessage.set('Failed to fetch metadata. Please try again later.');
          }
        });
    }
  }
}
