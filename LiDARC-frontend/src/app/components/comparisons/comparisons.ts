import { Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { ComparisonDTO } from '../../dto/comparison';
import { ComparisonService } from '../../service/comparison.service';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { Router, RouterModule } from '@angular/router';
import { finalize, interval, startWith, Subject, switchMap, takeUntil, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import {Globals, pollingIntervalMs, snackBarDurationMs} from '../../globals/globals';

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
    MatProgressSpinner],
  templateUrl: './comparisons.html',
  styleUrls: ['./comparisons.scss', '../stored-files/stored-files.scss'],
})
export class Comparisons {
  displayedColumns: string[] = ['name', 'status', 'filesLength', 'highestVegetation', 'outlierDetection', 'statisticsOverScenery', 'mostDifferences', 'resultReportUrl', 'createdAt'];//, 'actions'];
  dataSource = new MatTableDataSource<ComparisonDTO>([]);
  private readonly comparisonService = inject(ComparisonService);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  private stopPolling$ = new Subject<void>();
  private previousMap = new Map<number, string>(); // id → status

  constructor(private snackBar: MatSnackBar,
              public globals: Globals) { }

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit(): void {
    // First load
    this.fetchAndProcessComparisons();

    // Poll every 3 seconds
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.comparisonService.getAllComparisons()),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (data: ComparisonDTO[]) => {
          this.processComparisons(data);
        },
        error: (error) => {
          console.error('Error fetching comparisons:', error);
          this.errorMessage.set('Failed to fetch comparisons. Please try again later.');
        }
      });
  }

  fetchAndProcessComparisons(): void {
    this.loading.set(true);
    this.comparisonService.getAllComparisons()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data: ComparisonDTO[]) => this.processComparisons(data),
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
  private processComparisons(data: ComparisonDTO[]): void {
    this.dataSource.data = data;

    // Detect transitions and update previousMap
    data.forEach(item => {
      const prev = this.previousMap.get(item.id);

      if (prev === 'PENDING' && item.status === 'COMPLETED') {
        this.snackBar.open(
          `Comparison "${item.name}" completed!`,
          'OK', { duration: snackBarDurationMs }
        );
      } else if (prev === 'PENDING' && item.status === 'FAILED') {
        this.snackBar.open(
          `Comparison "${item.name}" failed!`,
          'OK', { duration: snackBarDurationMs }
        );
      }

      this.previousMap.set(item.id, item.status);
    });

    // Stop polling if no PENDING left
    if (!data.some(d => d.status === 'PENDING')) {
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
  }
}
