import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-file-details',
  imports: [],
  templateUrl: './file-details.html',
  styleUrl: './file-details.scss',
})
export class FileDetails {
  readonly userId: string | null;
  private route = inject(ActivatedRoute);
  constructor() {
    this.userId = this.route.snapshot.paramMap.get('id');
  }
}
