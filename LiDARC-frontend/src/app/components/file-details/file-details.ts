import { Component, inject, Input, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { finalize } from 'rxjs';
import { FormatService } from '../../service/format.service';
import { MetadataService } from '../../service/metadata.service';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatIcon } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ActivatedRoute } from '@angular/router';
import { TextCard } from '../text-card/text-card';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';

@Component({
  selector: 'app-file-details',
  imports: [
    CommonModule,
    MatIcon,
    MatExpansionModule,
    MatListModule,
    TextCard,
    MatProgressSpinner,
    FormatBytesPipe,
],
  templateUrl: './file-details.html',
  styleUrl: './file-details.scss',
})
export class FileDetails {
  @Input() metadataId: number | null = null;
  private metadataService = inject(MetadataService);
  @Input() metadata: FileMetadataDTO | null = null;
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  constructor(private route: ActivatedRoute, private formatService: FormatService) {
    this.metadataId = Number(this.route.snapshot.paramMap.get('id'));
  }

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
