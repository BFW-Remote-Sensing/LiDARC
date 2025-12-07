import { Component, Input, signal, WritableSignal } from '@angular/core';
import { FileDetailsCard } from '../file-details-card/file-details-card';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatAnchor, MatButtonModule } from "@angular/material/button";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDivider } from '@angular/material/divider';
import { MatCheckbox } from '@angular/material/checkbox';
import { CreateComparison } from '../../dto/comparison';
import { MatSpinner } from '@angular/material/progress-spinner';
import { ComparisonService } from '../../service/comparison.service';
import { finalize } from 'rxjs';
import { Router } from '@angular/router';

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
    MatSpinner
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
    fileMetadataIds: []
  };
  public loadingStart: WritableSignal<boolean> = signal(false);
  public errorMessage: WritableSignal<string | null> = signal(null);

  constructor(private selectedFilesService: SelectedFilesService, private comparisonService: ComparisonService, private router: Router) { }

  startComparisonDisabled(): boolean {
    return this.comparison.name.trim() === '' || (
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
    alert('\'Define Grid\' is not yet implemented.');
  }

  async startComparison(): Promise<void> {
    this.loadingStart.set(true);
    await new Promise(resolve => setTimeout(resolve, 2000));
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
}
