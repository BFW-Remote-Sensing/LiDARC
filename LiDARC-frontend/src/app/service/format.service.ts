import { Injectable } from '@angular/core';
import { FileMetadataDTO } from '../dto/fileMetadata';
import { ComparableItemDTO, ComparableListItem } from '../dto/comparableItem';
import { FolderFilesDTO } from '../dto/folderFiles';

@Injectable({
  providedIn: 'root'
})
export class FormatService {

  formatBytes(bytes: number, decimals = 2): string {
    if (bytes === undefined || bytes === null) return 'N/A';
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }

  formatComparableItems(items: ComparableItemDTO[]): ComparableListItem[] {
    return items.map(item => {
      if ('files' in item) {
        const formattedFiles = item.files.map(file => this.formatMetadata(file));
        const years = formattedFiles
          .map(f => f.captureYear)
          .filter((y): y is number => y !== null && y !== undefined);

        const min = years.length > 0 ? Math.min(...years) : null;
        const max = years.length > 0 ? Math.max(...years) : null;

        return {
          ...item,
          name: item.folderName,
          type: 'Folder',
          files: formattedFiles,
          captureYear:
            min === null || max === null
              ? null
              : min === max
                ? `${min}`
                : `${min}-${max}`,
          sizeBytes: formattedFiles.some(ff => ff.sizeBytes !== null)
            ? formattedFiles.reduce((acc, file) => acc + (file.sizeBytes || 0), 0)
            : null,
          uploadedAt: item.createdDate,
          fileCount: formattedFiles.length
        };
      } else {
        return {
          ...this.formatMetadata(item),
          name: item.originalFilename,
          type: 'File',
          fileCount: 1
        };
      }
    });
  }

  formatFolderFiles(folder: FolderFilesDTO): FolderFilesDTO {
    const formattedFiles = folder.files.map(file => this.formatMetadata(file));
    return {
      ...folder,
      files: formattedFiles
    };
  }

  formatMetadata(metadata: FileMetadataDTO): FileMetadataDTO {
    return {
      ...metadata,
      status: metadata.status === 'UPLOADED' ? 'PROCESSED' : metadata.status,
      systemIdentifier: metadata.systemIdentifier,
      type: 'File'
    };
  }
}
