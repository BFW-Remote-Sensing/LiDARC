import { Injectable } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";
import { ComparableListItem } from "../dto/comparableItem";

@Injectable({ providedIn: 'root' })
export class SelectedFilesService {
  selectedFileIds: number[] = [];
  selectedFiles: FileMetadataDTO[] = [];

  selectedComparableItemIds: string[] = [];
  selectedComparableItems: ComparableListItem[] = [];

  clearSelectedFileIds(): void {
    this.selectedFileIds = [];
  }

  clearSelectedComparableItemIds(): void {
    this.selectedComparableItemIds = [];
  }
}