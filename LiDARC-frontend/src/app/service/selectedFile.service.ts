import { Injectable } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";

@Injectable({ providedIn: 'root' })
export class SelectedFilesService {
  selectedIds: number[] = [];
  selectedFiles: FileMetadataDTO[] = [];

  clearSelectedIds(): void {
    this.selectedIds = [];
  }
}