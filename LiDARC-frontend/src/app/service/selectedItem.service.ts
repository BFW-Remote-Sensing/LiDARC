import { Injectable } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";
import { ComparableListItem } from "../dto/comparableItem";

@Injectable({ providedIn: 'root' })
export class SelectedItemService {
  items: Set<ComparableListItem> = new Set();

  delete(item: ComparableListItem): void {
    this.items.forEach((selectedItem) => {
      if (selectedItem.id === item.id && selectedItem.type === item.type) {
        this.items.delete(selectedItem);
      }
    });
  }
}