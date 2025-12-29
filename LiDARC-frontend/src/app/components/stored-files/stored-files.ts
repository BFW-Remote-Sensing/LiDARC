import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms'; import { MatButtonModule } from '@angular/material/button';
import { MatCheckbox, MatCheckboxChange } from '@angular/material/checkbox'; import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator'; import { MatSelectModule } from '@angular/material/select';
import { MatTableDataSource, MatTableModule } from '@angular/material/table'; import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterModule } from '@angular/router'; import { finalize, interval, Subject, switchMap, takeUntil } from 'rxjs'; import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MetadataService } from '../../service/metadata.service'; import { FileMetadataDTO } from '../../dto/fileMetadata';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormatService } from '../../service/format.service';
import { pollingIntervalMs, snackBarDurationMs } from '../../globals/globals';
import { TextCard } from '../text-card/text-card';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';
import { MatDialog } from '@angular/material/dialog';
import { MetadataResponse } from '../../dto/metadataResponse';
import { CreateFolderDialog } from '../create-folder-dialog/create-folder-dialog';
import { AssignFolderDialog } from '../assign-folder-dialog/assign-folder-dialog';

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
    MatProgressSpinner,
    TextCard,
    FormatBytesPipe
  ],
  templateUrl: './stored-files.html',
  styleUrl: './stored-files.scss',
})

/**
 * Component to display and manage unassigned files. They are files not yet assigned to any folder.
 */
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

  constructor(
    private selectedFilesService: SelectedFilesService,
    private router: Router,
    private snackBar: MatSnackBar,
    private formatService: FormatService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef
  ) { }

  totalItems = 0;
  pageIndex = 0;
  pageSize = 10;

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchAndProcessMetadata(this.pageIndex, this.pageSize);
  }

  ngOnInit(): void {
    // First load
    this.fetchAndProcessMetadata(this.pageIndex, this.pageSize);

    // Poll every 3 seconds
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.metadataService.getPagedMetadataWithoutFolder(this.pageIndex, this.pageSize, 'uploadedAt', false)),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (metadataResponse: MetadataResponse) => {
          this.totalItems = metadataResponse.totalItems;
          this.processMetadata(metadataResponse.items);
        },
        error: (error) => {
          console.error('Error refreshing metadata:', error);
          this.errorMessage.set('Failed to refresh metadata. Please try again later.');
        }
      });
  }

  fetchAndProcessMetadata(pageIndex: number, pageSize: number): void {
    this.loading.set(true);

    this.metadataService.getPagedMetadataWithoutFolder(pageIndex, pageSize, 'uploadedAt', false)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (metadataResponse: MetadataResponse) => {
          this.totalItems = metadataResponse.totalItems;
          this.processMetadata(metadataResponse.items);
        },
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
  //TODO: FIX THE STATUS MANAGEMENT IN THE CORRESPONDING ISSUE see #44
  private processMetadata(data: FileMetadataDTO[]): void {
    // Map formatted size
    this.dataSource.data = data.map(item => this.formatService.formatMetadata(item));

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

  toggleSelectAll(event: MatCheckboxChange): void {
    if (!event.checked) {
      this.selectedFileIds.clear();
      return;
    }

    for (const row of this.dataSource.data) {
      this.selectedFileIds.add(row.id);
    }
  }

  isAllSelected(): boolean {
    const selectableIds = this.dataSource.data
      .map(e => e.id);

    return selectableIds.length > 0 &&
      selectableIds.every(id => this.selectedFileIds.has(id));
  }

  isIndeterminate(): boolean {
    const selectableIds = this.dataSource.data
      .map(e => e.id);

    if (this.selectedFileIds.size === 0) {
      return false;
    }

    let hasAtLeastOne = false;

    for (const id of selectableIds) {
      if (this.selectedFileIds.has(id)) {
        hasAtLeastOne = true;
        break;
      }
    }

    return hasAtLeastOne && !this.isAllSelected();
  }


  toggleSelection(id: number, event: any) {
    if (event.checked) {
      this.selectedFileIds.add(id);
    } else {
      this.selectedFileIds.delete(id);
    }
  }

  isSelected(id: number): boolean {
    return this.selectedFileIds.has(id);
  }

  createFolder() {
    const dialogRef = this.dialog.open(CreateFolderDialog, {
      width: 'auto',
      height: 'auto',
      data: this.dataSource.data.filter(file => this.selectedFileIds.has(file.id))
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.selectedFileIds.clear();
        this.router.navigate([`/folders/${result.id}`]);
      }
    });
  }

  assignFolder() {
    const dialogRef = this.dialog.open(AssignFolderDialog, {
      width: '400px',
      data: Array.from(this.selectedFileIds)
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.selectedFileIds.clear();
        this.router.navigate([`/folders/${result.id}`]);
      }
    });
  }

  deleteSelectedFiles(): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to delete the selected files?',
      primaryButtonText: 'Delete',
      secondaryButtonText: 'Cancel'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        alert('Delete functionality not yet implemented.');
        this.selectedFileIds.clear();
        this.cdr.detectChanges(); // force Angular to update the view
      }
    });
  }
}
