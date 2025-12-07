import { CommonModule } from '@angular/common';
import { Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms'; import { MatButtonModule } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox'; import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator'; import { MatSelectModule } from '@angular/material/select';
import { MatTableDataSource, MatTableModule } from '@angular/material/table'; import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterModule } from '@angular/router'; import { finalize, interval, Subject, switchMap, takeUntil } from 'rxjs'; import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MetadataService } from '../../service/metadata.service'; import { FileMetadataDTO } from '../../dto/fileMetadata';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormatService } from '../../service/format.service';
import { pollingIntervalMs, snackBarDurationMs } from '../../globals/globals';

@Component({
  selector: 'app-stored-files',
  standalone: true,
  imports: [
    FormsModule,
    MatTableModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatPaginatorModule,
    MatCheckbox,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatProgressSpinner],
  templateUrl: './stored-files.html',
  styleUrl: './stored-files.scss',
})

export class StoredFiles {
  displayedColumns: string[] = ['select', 'filename', 'status', 'captureYear', 'sizeBytes', 'uploadedAt', 'actions'];
  dataSource = new MatTableDataSource<FileMetadataDTO>([]);
  selectedFileIds: Set<number> = new Set();
  private readonly metadataService = inject(MetadataService);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  private stopPolling$ = new Subject<void>();

  private previousMap = new Map<number, string>();

  constructor(private selectedFilesService: SelectedFilesService, private router: Router, private snackBar: MatSnackBar, private formatService: FormatService) { }

  ngOnInit(): void {
    // First load
    this.fetchAndProcessMetadata();

    // Poll every 3 seconds
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.metadataService.getAllMetadata()),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (data: FileMetadataDTO[]) => {
          this.processMetadata(data);
        },
        error: (error) => {
          console.error('Error refreshing metadata:', error);
          this.errorMessage.set('Failed to refresh metadata. Please try again later.');
        }
      });
  }

  fetchAndProcessMetadata(): void {
    this.loading.set(true);

    this.metadataService.getAllMetadata()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data: FileMetadataDTO[]) => this.processMetadata(data),
        error: (error) => {
          console.error('Error fetching metadata:', error);
          this.errorMessage.set('Failed to fetch metadata. Please try again later.');
        }
      });
  }

  /**
   * Processes metadata: maps formatted size, detects transitions,
   * updates previousMap, and checks for stopPolling
   */
  private processMetadata(data: FileMetadataDTO[]): void {
    // Map formatted size
    this.dataSource.data = data.map(item => ({
      ...item,
      formattedSize: this.formatService.formatBytes(item.sizeBytes)
    }));

    // Detect transitions and update previousMap
    data.forEach(item => {
      const prev = this.previousMap.get(item.id);

      if (prev === 'PROCESSING' && item.status === 'PROCESSED') {
        this.snackBar.open(
          `File "${item.filename}" preprocessed completed!`,
          'OK', { duration: snackBarDurationMs }
        );
      } else if (prev === 'PROCESSING' && item.status === 'FAILED') {
        this.snackBar.open(
          `File "${item.filename}" preprocessed failed!`,
          'OK', { duration: snackBarDurationMs }
        );
      }

      this.previousMap.set(item.id, item.status);
    });

    // Stop polling if no PROCESSING left
    if (!data.some(d => d.status === 'PROCESSING')) {
      this.stopPolling$.next();
    }
  }


  ngOnDestroy(): void {
    this.stopPolling$.next();   // clean up
    this.stopPolling$.complete();
  }

  ngAfterViewInit() {
    setTimeout(() => {
      if (this.paginator) {
        this.dataSource.paginator = this.paginator;
      }
    });
    const storedSelectedFileIds = localStorage.getItem('selectedFileIds');
    if (storedSelectedFileIds) {
      this.selectedFileIds = new Set(JSON.parse(storedSelectedFileIds));
    }
  }

  toggleSelection(id: number, event: any) {
    if (event.checked) {
      this.selectedFileIds.add(id);
    } else {
      this.selectedFileIds.delete(id);
    }
    localStorage.setItem('selectedFileIds', JSON.stringify(Array.from(this.selectedFileIds)));
  }

  isSelected(id: number): boolean {
    return this.selectedFileIds.has(id);
  }

  deleteSelectedFiles() {
    alert('Delete functionality not yet implemented.');
    this.selectedFileIds.clear();
    localStorage.removeItem('selectedFileIds');
  }

  goToComparison() {
    if (this.selectedFileIds.size === 2) {
      this.selectedFilesService.selectedIds = Array.from(this.selectedFileIds);
      this.selectedFilesService.selectedFiles = this.dataSource.data.filter(file => this.selectedFileIds.has(file.id));
      this.router.navigate(['/comparison-setup']);
    }
  }
}