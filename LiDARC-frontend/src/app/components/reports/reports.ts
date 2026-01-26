import {AfterViewInit, Component, OnInit, signal, ViewChild} from '@angular/core';
import {Globals} from '../../globals/globals';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {ComparisonReport} from '../../dto/comparisonReport';
import {MatPaginator, MatPaginatorModule} from '@angular/material/paginator';
import {MatSortModule} from '@angular/material/sort';
import {catchError, debounceTime, distinctUntilChanged, map, merge, of, startWith, Subject, switchMap} from 'rxjs';
import {ReportService} from '../../service/report.service';
import {FormsModule} from '@angular/forms';
import {MatSelectModule} from '@angular/material/select';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {RouterModule} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatCardModule} from '@angular/material/card';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {TextCard} from '../text-card/text-card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {PdfThumbnail} from '../pdf-thumbnail/pdf-thumbnail';
import {ConfirmationDialogComponent, ConfirmationDialogData} from '../confirmation-dialog/confirmation-dialog';
import {MatDialog} from '@angular/material/dialog';

@Component({
  selector: 'app-reports',
  imports: [FormsModule,
    MatTableModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatPaginatorModule,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatCardModule,
    MatProgressSpinner,
    TextCard,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule, PdfThumbnail],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports implements OnInit, AfterViewInit {

  dataSource = new MatTableDataSource<ComparisonReport>([]);
  resultsLength = 0;
  loading = signal<boolean>(true);
  errorMessage = signal<string | null>(null);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  private searchSubject = new Subject<string>();
  private reloadSubject = new Subject<void>();
  private defaultSortActive = 'creationDate';
  private defaultSortDirection = 'desc';

  constructor(private reportService: ReportService,
              private dialog: MatDialog,
              public globals: Globals,) {
  }

  ngOnInit(): void {

    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged()
    ).subscribe(filterValue => {
      this.paginator.pageIndex = 0; // Reset to page 0
      this.loadReportsPage();       // Trigger reload
    });
  }

  ngAfterViewInit() {
    // Link Paginator and Sort to the DataSource
    merge(this.paginator.page, this.reloadSubject)
      .pipe(
        startWith({}),
        switchMap(() => {
          this.loading.set(true);
          return this.reportService.getAllReports(
            this.paginator.pageIndex,
            this.paginator.pageSize,
            this.defaultSortActive,
            this.defaultSortDirection,
            this.dataSource.filter // Used to store current search term
          ).pipe(catchError(() => of(null)));
        }),
        map(data => {
          this.loading.set(false);
          if (data === null) return [];

          this.resultsLength = data.totalElements; // Update length for Paginator
          return data.content;
        })
      ).subscribe(data => this.dataSource.data = data);
  }

  loadReportsPage() {
    this.reloadSubject.next();
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
    this.searchSubject.next(filterValue);
  }

  openPdf(reportId: number): void {
    const url = `${this.globals.backendUri}/reports/${reportId}/view`;

    window.open(url, '_blank');
  }

  deleteReport(id: number, name: string): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to delete this report?',
      objectName: name,
      primaryButtonText: 'Delete',
      primaryButtonColor: 'warn',
      secondaryButtonText: 'Cancel',
      onConfirm: () => this.reportService.deleteReport(id),
      successActionText: 'Report deletion'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data,
      disableClose: true,
      autoFocus: false
    });

    dialogRef.afterClosed().subscribe(success => {
      if (success) {
        this.dataSource.data = this.dataSource.data.filter(report => report.id !== id);
        //this.cdr.detectChanges(); // force Angular to update the view
      }
    });
  }
}
