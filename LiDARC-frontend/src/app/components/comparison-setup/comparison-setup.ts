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
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { GridDefinitionDialogComponent } from '../define-grid/define-grid';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from "@angular/material/icon";
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-comparison-setup',
  imports: [
    FileDetailsCard,
    MatAnchor,
    MatButtonModule,
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    MatDivider,
    MatCheckbox,
    MatProgressSpinner,
    MatCardModule,
    MatIcon
  ],
  templateUrl: './comparison-setup.html',
  styleUrl: './comparison-setup.scss',
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

  constructor(
    private selectedFilesService: SelectedFilesService,
    private comparisonService: ComparisonService,
    private router: Router,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
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
    if (this.selectedFilesService.selectedIds.length >= 2) {
      this.comparison.fileMetadataIds = [
        ...this.selectedFilesService.selectedIds
      ];
    }
  }

  defineGrid(): void {
    // Open the dialog
    const dialogRef = this.dialog.open(GridDefinitionDialogComponent, {
      width: '600px',
      height: 'auto',
      data: {
        file: this.selectedFilesService.selectedFiles[0] // Pass the first selected file to the dialog
      }
    });

    // Handle the result when the dialog is closed
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.comparison = {
          ...this.comparison,
          grid: {
            cellWidth: result.cellWidth,
            cellHeight: result.cellHeight,
            xMin: result.xMin,
            xMax: result.xMax,
            yMin: result.yMin,
            yMax: result.yMax
          }
        };
        this.cdr.markForCheck();
        console.log('Grid defined:', this.comparison.grid);
      } else {
        console.log('Grid definition cancelled or no dimensions provided.');
      }
    });
  }


  startComparison(): void {
    this.loadingStart.set(true);
    console.log('Starting comparison with settings:', this.comparison);
    this.comparisonService.postComparison(this.comparison)
      .pipe(
        finalize(() => this.loadingStart.set(false))
      )
      .subscribe({
        next: (comparison) => {
          console.log('Comparison started successfully:', comparison);
          alert(`Comparison "${comparison.name}" started successfully.`);
          localStorage.removeItem('selectedFileIds');
          this.selectedFilesService.clearSelectedIds();
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
}
