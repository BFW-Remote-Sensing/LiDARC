import { Component, Input } from '@angular/core';
import { FileDetailsCard } from '../file-details-card/file-details-card';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatAnchor, MatButtonModule } from "@angular/material/button";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDivider } from '@angular/material/divider';
import { MatCheckbox } from '@angular/material/checkbox';
import { Comparison } from '../../dto/comparison';

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
    MatCheckbox
  ],
  templateUrl: './comparison-setup.html',
  styleUrl: './comparison-setup.scss',
})
export class ComparisonSetup {
  firstMetadataId: string | null = null;
  secondMetadataId: string | null = null;
  @Input() comparison: Comparison = {
    name: '',
    highestVegetation: false,
    outlierDetection: false,
    statisticsOverScenery: false,
    mostDifferences: false,
    grid: null
  };

  constructor(private selectedFilesService: SelectedFilesService) { }

  ngOnInit(): void {
    if (this.selectedFilesService.selectedIds.length >= 2) {
      this.firstMetadataId = this.selectedFilesService.selectedIds[0].toString();
      this.secondMetadataId = this.selectedFilesService.selectedIds[1].toString();
    }
  }

  defineGrid(): void {
    alert('Define Grid functionality is not yet implemented.');
  }

  startComparison(): void {
    alert('Define the grid first! Not yet implemented.');
  }
}
