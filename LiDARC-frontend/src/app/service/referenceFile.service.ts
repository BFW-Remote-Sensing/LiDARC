import { Injectable, signal } from "@angular/core";
import { ComparableListItem } from "../dto/comparableItem";

@Injectable({ providedIn: 'root' })
export class ReferenceFileService {
    selectedComparableItem = signal<ComparableListItem | null>(null);

    setSelectedComparableItem(item: ComparableListItem): void {
        this.selectedComparableItem.set(item);
    }

    isSelectedComparableItem(item: ComparableListItem): boolean {
        return this.selectedComparableItem()?.id === item.id;
    }

    clearSelectedComparableItem(): void {
        this.selectedComparableItem.set(null);
    }
}