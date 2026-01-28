import { Injectable } from "@angular/core";
import { ComparableListItem } from "../dto/comparableItem";
import { FolderFilesDTO } from "../dto/folderFiles";

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

  deleteById(id: number, type: string): boolean {
    console.log("Deleting item with id:", id, "and type:", type);
    for (let selectedItem of this.items) {
      if (selectedItem.id === id && selectedItem.type === type) {
        this.items.delete(selectedItem);
        return true;
      }
      if (selectedItem.type === "Folder" && type === "File") {
        const thisFolderSelectedItem = selectedItem as FolderFilesDTO;
        for (let fileItem of thisFolderSelectedItem.files) {
          if (fileItem.id === id) {
            thisFolderSelectedItem.files = thisFolderSelectedItem.files.filter(f => f.id !== id);
            return false;
          }
        }
      }
    }
    return false;
  }
}