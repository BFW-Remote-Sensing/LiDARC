import { Injectable } from '@angular/core';
import { FileMetadataDTO } from '../dto/fileMetadata';

@Injectable({
  providedIn: 'root'
})
export class FormatService {

  formatBytes(bytes: number, decimals = 2): string {
    if(bytes === undefined || bytes === null) return 'N/A';
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }

  formatMetadata(metadata: FileMetadataDTO): FileMetadataDTO {
    return {
      ...metadata,
      status: metadata.status === 'UPLOADED' ? 'PROCESSED' : metadata.status,
      systemIdentifier: 'EPSG 31256',
    };
  }
}
