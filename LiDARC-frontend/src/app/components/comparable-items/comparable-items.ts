import {CommonModule} from '@angular/common';
import {ChangeDetectorRef, Component, inject, signal, ViewChild, WritableSignal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckbox} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatPaginator, MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {MatSelectModule} from '@angular/material/select';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatTooltipModule} from '@angular/material/tooltip';
import {Router, RouterModule} from '@angular/router';
import {FormatBytesPipe} from '../../pipes/formatBytesPipe';
import {TextCard} from '../text-card/text-card';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {debounceTime, distinctUntilChanged, finalize, interval, Subject, switchMap, takeUntil} from 'rxjs';
import {FormatService} from '../../service/format.service';
import {MetadataService} from '../../service/metadata.service';
import {SelectedItemService} from '../../service/selectedItem.service';
import {pollingIntervalMs, snackBarDurationMs} from '../../globals/globals';
import {ConfirmationDialogData, ConfirmationDialogComponent} from '../confirmation-dialog/confirmation-dialog';
import {ComparableItemDTO, ComparableListItem} from '../../dto/comparableItem';
import {ComparableResponse} from '../../dto/comparableResponse';
import {MatInputModule} from '@angular/material/input';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatChipsModule} from '@angular/material/chips';
import {CoordinateService} from '../../service/coordinate.service';
import {getExtremeValue} from '../../helpers/extremeValue';
import {FileMetadataDTO} from '../../dto/fileMetadata';


@Component({
  selector: 'app-comparable-items',
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
    MatChipsModule
  ],
  templateUrl: './comparable-items.html',
  styleUrl: './comparable-items.scss',
})
export class ComparableItems {
  displayedColumns: string[] = ['select', 'name', 'type', 'fileCount', 'status', 'captureYear', 'sizeBytes', 'uploadedAt', 'actions'];
  dataSource = new MatTableDataSource<ComparableListItem>([]);
  private readonly metadataService = inject(MetadataService);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);
  private searchSubject = new Subject<string>();

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  private stopPolling$ = new Subject<void>();

  private previousMap = new Map<number, string>();

  constructor(
    public selectedItemService: SelectedItemService,
    private router: Router,
    private snackBar: MatSnackBar,
    private formatService: FormatService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    private coordService: CoordinateService
  ) {
  }

  totalItems = 0;
  pageIndex = 0;
  pageSize = 10;

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchAndProcessMetadata(this.pageIndex, this.pageSize);
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

    // Poll every 3 seconds
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.metadataService.getAllMetadataGroupedByFolderPaged(this.pageIndex, this.pageSize, this.dataSource.filter)),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (comparableResponse: ComparableResponse) => {
          this.totalItems = comparableResponse.totalItems;
          this.processMetadata(comparableResponse.items);
        },
        error: (error) => {
          console.error('Error refreshing metadata:', error);
          this.errorMessage.set('Failed to refresh metadata. Please try again later.');
        }
      });
  }

  fetchAndProcessMetadata(pageIndex: number, pageSize: number): void {
    this.loading.set(true);

    this.metadataService.getAllMetadataGroupedByFolderPaged(pageIndex, pageSize, this.dataSource.filter)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (comparableResponse: ComparableResponse) => {
          this.totalItems = comparableResponse.totalItems;
          this.processMetadata(comparableResponse.items);
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
  private processMetadata(data: ComparableItemDTO[]): void {
    // Map formatted size
    const formattedComparableItems = this.formatService.formatComparableItems(data);
    this.dataSource.data = formattedComparableItems;

    // Detect transitions and update previousMap
    formattedComparableItems.forEach(item => {
      const prev = this.previousMap.get(item.id);

      if (prev === 'PROCESSING' && item.status === 'PROCESSED') {
        this.snackBar.open(
          `File "${item.name}" preprocessed completed!`,
          'OK', {duration: snackBarDurationMs}
        );
      } else if (prev === 'PROCESSING' && item.status === 'FAILED') {
        this.snackBar.open(
          `File "${item.name}" preprocessed failed!`,
          'OK', {duration: snackBarDurationMs}
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

  toggleSelection(item: ComparableListItem, event: any) {
    if (event.checked) {
      this.selectedItemService.items.add(item);
    } else {
      this.selectedItemService.delete(item);
    }
  }

  isSelected(item: ComparableListItem): boolean {
    return Array.from(this.selectedItemService.items).some(
      selected => selected.id === item.id && selected.type === item.type
    );
  }

  goToComparison() {
    console.log('Selected Comparable Items:', this.selectedItemService.items);
    this.router.navigate(['/comparison-setup']);
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
        this.selectedItemService.items.clear();
        this.cdr.detectChanges(); // force Angular to update the view
      }
    });
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

  getIconForType(type: string): string {
    switch (type) {
      case 'Folder':
        return 'folder';
      case 'File':
        return 'insert_drive_file';
      default:
        return 'help_outline';
    }
  }
}
