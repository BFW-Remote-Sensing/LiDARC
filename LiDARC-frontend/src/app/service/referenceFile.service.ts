import { Injectable, signal } from "@angular/core";
import { FileMetadataDTO } from "../dto/fileMetadata";

@Injectable({ providedIn: 'root' })
export class ReferenceFileService {
    selectedFile = signal<FileMetadataDTO | null>(null);
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