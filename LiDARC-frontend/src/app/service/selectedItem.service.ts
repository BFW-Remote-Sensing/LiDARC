import { Injectable } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";
import { ComparableListItem } from "../dto/comparableItem";

@Injectable({ providedIn: 'root' })
export class SelectedItemService {
  items: Set<ComparableListItem> = new Set();
}