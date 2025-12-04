import { Component } from '@angular/core';
import { FileDetailsCard } from '../file-details-card/file-details-card';
import { SelectedFilesService } from '../../service/selectedFile.service';
import { MatAnchor, MatButtonModule } from "@angular/material/button";

@Component({
  selector: 'app-comparison-setup',
  imports: [
    FileDetailsCard,
    MatAnchor,
    MatButtonModule
],
  templateUrl: './comparison-setup.html',
  styleUrl: './comparison-setup.scss',
})
export class ComparisonSetup {
  firstMetadataId: string | null = null;
  secondMetadataId: string | null = null;
  grid: any | null = null;

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
