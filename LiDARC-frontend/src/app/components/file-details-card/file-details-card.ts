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
    MatIcon
  ],
  templateUrl: './file-details-card.html',
  styleUrl: './file-details-card.scss',
})
export class FileDetailsCard implements OnInit {
  @Input() metadataId: string | null = null;
  private metadataService = inject(MetadataService);
  @Input() metadata: FileMetadataDTO | null = null;
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    if (this.metadataId) {
      this.metadataService.getMetadataById(+this.metadataId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (data) => {
            console.log('Fetched metadata:', data);
            this.metadata = data;
          },
          error: (error) => {
            console.error('Error fetching metadata by ID:', error);
            this.errorMessage.set('Failed to fetch metadata. Please try again later.');
          }
        });
    }
  }

  formatBytes(bytes: number, decimals = 2): string {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GВ', 'TB', 'PB', 'EB', 'ZB', 'YB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }
}
