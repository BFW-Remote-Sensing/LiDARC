import { Injectable } from "@angular/core";

@Injectable({ providedIn: 'root' })
export class SelectedFilesService {
  selectedIds: number[] = [];

  clearSelectedIds(): void {
    this.selectedIds = [];
  }
}