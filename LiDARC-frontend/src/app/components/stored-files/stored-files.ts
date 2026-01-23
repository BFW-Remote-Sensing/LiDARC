import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms'; import { MatButtonModule } from '@angular/material/button';
import { MatCheckbox, MatCheckboxChange } from '@angular/material/checkbox'; import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator'; import { MatSelectModule } from '@angular/material/select';
import { MatTableDataSource, MatTableModule } from '@angular/material/table'; import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterModule } from '@angular/router'; import { debounceTime, delay, distinctUntilChanged, finalize, interval, Subject, switchMap, takeUntil } from 'rxjs'; import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MetadataService } from '../../service/metadata.service'; import { FileMetadataDTO } from '../../dto/fileMetadata';
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
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { StatusService } from '../../service/status.service';

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
    FormatBytesPipe,
    MatFormFieldModule,
    MatInputModule,
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
  private searchSubject = new Subject<string>();

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  private isPolling = false;
  private stopPolling$ = new Subject<void>();

  private previousMap = new Map<number, string>();

  constructor(
    private router: Router,
    private snackBar: MatSnackBar,
    private formatService: FormatService,
    private statusService: StatusService,
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

  private startPolling(): void {
    if (this.isPolling) {
      return;
    }

    this.isPolling = true;
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.metadataService.getPagedMetadataWithoutFolder(
          this.pageIndex, this.pageSize, 'uploadedAt', false, this.dataSource.filter
        )),
        finalize(() => this.isPolling = false)
      )
      .subscribe({
        next: (metadataResponse: MetadataResponse) => {
          this.totalItems = metadataResponse.totalItems;
          this.processMetadata(metadataResponse.items);
        },
        error: (error) => {
          console.error('Error refreshing metadata:', error);
          this.errorMessage.set('Failed to refresh metadata. Please try again later.');
          this.isPolling = false;
        }
      });
  }

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(400),
        distinctUntilChanged())
      .subscribe(filterValue => {
        this.paginator.pageIndex = 0; // Reset to page 0 
        this.fetchAndProcessMetadata(this.pageIndex, this.pageSize); // Trigger reload
      });
    // First load
    this.fetchAndProcessMetadata(this.pageIndex, this.pageSize);
  }

  fetchAndProcessMetadata(pageIndex: number, pageSize: number): void {
    this.loading.set(true);
    this.dataSource.data = [];

    this.metadataService.getPagedMetadataWithoutFolder(pageIndex, pageSize, 'uploadedAt', false, this.dataSource.filter)
      .pipe(
        finalize(() => this.loading.set(false)))
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
  private processMetadata(newData: FileMetadataDTO[]): void {
    const currentData = this.dataSource.data;

    this.dataSource.data = newData.map(newItem => {
      const formattedItem = this.formatService.formatMetadata(newItem);

      const existingItem = currentData.find(item => item.id === formattedItem.id);

      if (existingItem && JSON.stringify(existingItem) === JSON.stringify(formattedItem)) {
        return existingItem;
      }

      const prevStatus = this.previousMap.get(formattedItem.id);
      if (prevStatus && prevStatus !== formattedItem.status) {
        this.snackBar.open(
          this.statusService.getComparableSnackbarMessage("File", formattedItem.originalFilename, formattedItem.status),
          'OK', { duration: snackBarDurationMs }
        );
      }
      this.previousMap.set(formattedItem.id, formattedItem.status);

      return formattedItem;
    });

    const hasProcessingFiles = this.dataSource.data.some(d => d.status !== 'PROCESSED' && d.status !== 'FAILED');
    if (hasProcessingFiles) {
      this.startPolling();
    } else {
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
      if (row.status === 'PROCESSED') {
        this.selectedFileIds.add(row.id);
      }
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

  applyFilter(event: Event) {
    this.pageIndex = 0;
    const filterValue = (event.target as HTMLInputElement).value;
    if (filterValue.trim() === this.dataSource.filter) {
      return; // No change in filter, do nothing
    }
    this.loading.set(true);
    this.dataSource.filter = filterValue.trim().toLowerCase();
    this.searchSubject.next(filterValue);
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
