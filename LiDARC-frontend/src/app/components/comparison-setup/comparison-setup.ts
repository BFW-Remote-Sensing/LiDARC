import { ChangeDetectorRef, Component, Input, signal, WritableSignal } from '@angular/core';
import { FileDetailsCard } from '../file-details-card/file-details-card';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatAnchor, MatButtonModule } from "@angular/material/button";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDivider } from '@angular/material/divider';
import { MatCheckbox } from '@angular/material/checkbox';
import { CreateComparison } from '../../dto/comparison';
import { MatProgressSpinner, MatSpinner } from '@angular/material/progress-spinner';
import { ComparisonService } from '../../service/comparison.service';
import { finalize } from 'rxjs';
import { Router, RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { GridDefinitionDialogComponent } from '../define-grid/define-grid';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from "@angular/material/icon";
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';
import { ReferenceFileService } from '../../service/referenceFile.service';
import { ComparableListItem } from '../../dto/comparableItem';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';

@Component({
  selector: 'app-comparison-setup',
  imports: [
    MatAnchor,
    MatButtonModule,
    MatTableModule,
    MatSelectModule,
    FormsModule,
    CommonModule,
    MatInputModule,
    MatFormFieldModule,
    MatDivider,
    MatCheckbox,
    RouterModule,
    MatProgressSpinner,
    MatCardModule,
    MatIcon,
    FormatBytesPipe
  ],
  templateUrl: './comparison-setup.html',
  styleUrls: ['./comparison-setup.scss', '../comparable-items/comparable-items.scss'],
})
export class ComparisonSetup {
  @Input() comparison: CreateComparison = {
    name: '',
    needHighestVegetation: false,
    needOutlierDetection: false,
    needStatisticsOverScenery: false,
    needMostDifferences: false,
    fileMetadataIds: [],
    grid: null
  };
  public loadingStart: WritableSignal<boolean> = signal(false);
  public errorMessage: WritableSignal<string | null> = signal(null);

  displayedColumns: string[] = ['select', 'name', 'type', 'status', 'captureYear', 'sizeBytes', 'uploadedAt', 'actions'];
  dataSource = new MatTableDataSource<ComparableListItem>([]);

  constructor(
    private selectedFilesService: SelectedFilesService,
    private comparisonService: ComparisonService,
    private router: Router,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    public refService: ReferenceFileService
  ) { }

  startComparisonDisabled(): boolean {
    return this.comparison.name.trim() === '' ||
      this.comparison.grid === null ||
      (
        this.comparison.needHighestVegetation === false &&
        this.comparison.needOutlierDetection === false &&
        this.comparison.needStatisticsOverScenery === false &&
        this.comparison.needMostDifferences === false
      );
  }

  ngOnInit(): void {
    if (this.selectedFilesService.selectedComparableItemIds.length >= 2) {
      // this.comparison.fileMetadataIds = [
      //   ...this.selectedFilesService.selectedComparableItemIds.map(item => Number(item.split('-')[0]))
      // ];
      this.dataSource.data = this.selectedFilesService.selectedComparableItems;
    }
  }

  defineGrid(): void {
    const dialogRef = this.dialog.open(GridDefinitionDialogComponent, {
      width: '600px',
      height: 'auto',
      data: {
        file: this.refService.selectedFile()
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.comparison = {
          ...this.comparison,
          grid: {
            cellWidth: Number(result.cellWidth.toFixed(2)),
            cellHeight: Number(result.cellHeight.toFixed(2)),
            xMin: Number(result.xMin.toFixed(3)),
            xMax: Number(result.xMax.toFixed(3)),
            yMin: Number(result.yMin.toFixed(3)),
            yMax: Number(result.yMax.toFixed(3))
          }
        };
        this.cdr.markForCheck();
      }
    });
  }


  startComparison(): void {
    this.loadingStart.set(true);
    this.comparisonService.postComparison(this.comparison)
      .pipe(
        finalize(() => this.loadingStart.set(false))
      )
      .subscribe({
        next: () => {
          this.selectedFilesService.clearSelectedFileIds();
          this.router.navigate(['/comparisons']);
        },
        error: (error) => {
          console.error('Error starting comparison:', error);
          this.errorMessage.set('Failed to fetch metadata. Please try again later.');
          this.loadingStart.set(false);
        }
      });
  }

  openConfirmationDialog(): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to start this comparison?',
      primaryButtonText: 'Start',
      secondaryButtonText: 'Cancel'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.startComparison();
      }
    });
  }

  ngOnDestroy(): void {
    this.refService.clearSelected();
  }
}
