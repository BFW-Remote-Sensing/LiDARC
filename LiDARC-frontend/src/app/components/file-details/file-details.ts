import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FileDetailsCard } from "../file-details-card/file-details-card";

@Component({
  selector: 'app-file-details',
  imports: [
    CommonModule,
    FileDetailsCard
],
  templateUrl: './file-details.html',
  styleUrl: './file-details.scss',
})
export class FileDetails {
  readonly metadataId: number | null;
  private route = inject(ActivatedRoute);

  constructor() {
    this.metadataId = Number(this.route.snapshot.paramMap.get('id'));
  }
}
