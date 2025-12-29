import { Component, inject, Input, signal, WritableSignal } from '@angular/core';
import { FolderFilesDTO } from '../../dto/folderFiles';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormatService } from '../../service/format.service';
import { finalize } from 'rxjs';
import { FolderService } from '../../service/folderService.service';
import { CommonModule } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIcon, MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { TextCard } from '../text-card/text-card';
import { getExtremeValue } from '../../helpers/extremeValue';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-folder-details',
  imports: [
    CommonModule,
    MatIconModule,
    MatExpansionModule,
    MatListModule,
    TextCard,
    MatProgressSpinner,
    FormatBytesPipe,
    MatTableModule,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatButtonModule
  ],
  templateUrl: './folder-details.html',
  styleUrls: ['./folder-details.scss', '../file-details/file-details.scss', '../stored-files/stored-files.scss'],
})
export class FolderDetails {
  @Input() folderId: number | null = null;
  private folderService = inject(FolderService);
  @Input() folder: FolderFilesDTO | null = null;
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  displayedColumns: string[] = ['filename', 'status', 'captureYear', 'sizeBytes', 'uploadedAt', 'actions'];
  dataSource = new MatTableDataSource<FileMetadataDTO>([]);

  constructor(private route: ActivatedRoute, private formatService: FormatService) {
    this.folderId = Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    if (this.folderId) {
      this.folderService.getFolderById(+this.folderId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (data) => {
            this.folder = this.formatService.formatFolderFiles(data);
            this.dataSource.data = this.folder.files;
          },
          error: (error) => {
            console.error('Error fetching metadata by ID:', error);
            this.errorMessage.set('Failed to fetch metadata. Please try again later.');
          }
        });
    }
  }

  getFolderSizeBytes(): number {
    if (!this.folder) {
      return 0;
    }
    return this.folder.files.reduce(
      (acc, file) => acc + (file.sizeBytes ?? 0),
      0
    );
  }

  getCaptureYear(): string | null {
    if (!this.folder || this.folder.files.length === 0) {
      return null;
    }
    const min = Math.min(...this.folder.files.map(f => f.captureYear || Infinity));
    const max = Math.max(...this.folder.files.map(f => f.captureYear || -Infinity));
    return min === max ? `${min}` : `${min}-${max}`;
  }

  getMinMinX(): number | null {
    return getExtremeValue(this.folder?.files, f => f.minX, 'min');
  }

  getMinMinY(): number | null {
    return getExtremeValue(this.folder?.files, f => f.minY, 'min');
  }

  getMinMinZ(): number | null {
    return getExtremeValue(this.folder?.files, f => f.minZ, 'min');
  }

  getMaxMaxX(): number | null {
    return getExtremeValue(this.folder?.files, f => f.maxX, 'max');
  }

  getMaxMaxY(): number | null {
    return getExtremeValue(this.folder?.files, f => f.maxY, 'max');
  }

  getMaxMaxZ(): number | null {
    return getExtremeValue(this.folder?.files, f => f.maxZ, 'max');
  }
}
