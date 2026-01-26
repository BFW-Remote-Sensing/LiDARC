import { Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { ComparisonDTO } from '../../dto/comparison';
import { ComparisonService } from '../../service/comparison.service';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Router, RouterModule } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, interval, startWith, Subject, switchMap, takeUntil, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Globals, pollingIntervalMs, snackBarDurationMs } from '../../globals/globals';
import { MatCardModule } from '@angular/material/card';
import { TextCard } from '../text-card/text-card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { ComparisonResponse } from '../../dto/comparisonResponse';
import { StatusService } from '../../service/status.service';

@Component({
  selector: 'app-comparisons',
  imports: [
    FormsModule,
    MatTableModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatPaginatorModule,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatProgressSpinner,
    MatCardModule,
    TextCard,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './comparisons.html',
  styleUrls: ['./comparisons.scss', '../stored-files/stored-files.scss'],
})
export class Comparisons {
  displayedColumns: string[] = ['name', 'status', 'filesLength', 'highestVegetation', 'outlierDetection', 'statisticsOverScenery', 'mostDifferences', 'resultReportUrl', 'createdAt', 'actions'];
  dataSource = new MatTableDataSource<ComparisonDTO>([]);
  private readonly comparisonService = inject(ComparisonService);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);
  private searchSubject = new Subject<string>();
  private isPolling = false;
  private stopPolling$ = new Subject<void>();
  private previousMap = new Map<number, string>(); // id → status

  constructor(private snackBar: MatSnackBar,
    private statusService: StatusService,
    public globals: Globals) { }

  totalItems = 0;
  pageIndex = 0;
  pageSize = 10;

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchAndProcessComparisons(this.pageIndex, this.pageSize);
  }

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit(): void {
    this.searchSubject
      .pipe(
        debounceTime(400),
        distinctUntilChanged())
      .subscribe(filterValue => {
        this.paginator.pageIndex = 0; // Reset to page 0 
        this.fetchAndProcessComparisons(this.pageIndex, this.pageSize); // Trigger reload
      });
    // First load
    this.fetchAndProcessComparisons(this.pageIndex, this.pageSize);
  }

  private startPolling(): void {
    if (this.isPolling) {
      return;
    }

    this.isPolling = true;
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.comparisonService.getPagedComparisons(
          this.pageIndex, this.pageSize, 'createdAt', false, this.dataSource.filter
        )),
        finalize(() => this.isPolling = false)
      )
      .subscribe({
        next: (comparisonResponse: ComparisonResponse) => {
          this.totalItems = comparisonResponse.totalItems;
          this.processComparisons(comparisonResponse.items);
        },
        error: (error) => {
          console.error('Error refreshing comparisons:', error);
          this.errorMessage.set('Failed to refresh comparisons. Please try again later.');
          this.isPolling = false;
        }
      });
  }

  fetchAndProcessComparisons(pageIndex: number, pageSize: number): void {
    this.loading.set(true);
    this.comparisonService.getPagedComparisons(pageIndex, pageSize, 'createdAt', false, this.dataSource.filter)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response: ComparisonResponse) => {
          this.totalItems = response.totalItems;
          this.processComparisons(response.items);
        },
        error: (error) => {
          console.error('Error fetching comparisons:', error);
          this.errorMessage.set('Failed to fetch comparisons. Please try again later.');
        }
      });
  }

  /**
   * Process comparisons:
   * - Track previous status to detect transitions
   * - Show snack bar notifications
   * - Stop polling when no PENDING comparisons remain
   */
  private processComparisons(newData: ComparisonDTO[]): void {
    const currentData = this.dataSource.data;

    this.dataSource.data = newData.map(newItem => {
      const existingItem = currentData.find(item => item.id === newItem.id);

      if (existingItem && JSON.stringify(existingItem) === JSON.stringify(newItem)) {
        return existingItem;
      }

      const prevStatus = this.previousMap.get(newItem.id);
      if (prevStatus && prevStatus !== newItem.status) {
        this.snackBar.open(
          this.statusService.getComparisonSnackbarMessage("Comparison", newItem.name, newItem.status),
          'OK', { duration: snackBarDurationMs }
        );
      }

      this.previousMap.set(newItem.id, newItem.status);

      return newItem;
    });

    const hasProcessingComparisons = this.dataSource.data.some(
      d => d.status !== 'COMPLETED' && d.status !== 'FAILED'
    );

    if (hasProcessingComparisons) {
      this.startPolling();
    } else {
      this.stopPolling$.next();
    }
  }


  ngOnDestroy(): void {
    this.stopPolling$.next();   // clean up
    this.stopPolling$.complete();
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
}
