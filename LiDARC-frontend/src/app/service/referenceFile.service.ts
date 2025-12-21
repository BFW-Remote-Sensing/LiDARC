import { Injectable, signal } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";

@Injectable({ providedIn: 'root' })
export class ReferenceFileService {
    selectedFile = signal<FileMetadataDTO | null>(null);
    selectedComparableItemId = signal<string | null>(null);

    setSelectedComparableItemId(id: string): void {
        this.selectedComparableItemId.set(id);
    }

    isSelectedComparableItemId(id: string): boolean {
        return this.selectedComparableItemId() === id;
    }

    clearSelectedComparableItemId(): void {
        this.selectedComparableItemId.set(null);
    }

    setSelectedIndex(file: FileMetadataDTO): void {
        this.selectedFile.set(file);
    }

    isSelected(id: number): boolean {
        return this.selectedFile()?.id === id;
    }

    clearSelected(): void {
        this.selectedFile.set(null);
    }
}