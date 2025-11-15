import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpHeaders } from '@angular/common/http';
import { FileInfo } from '../dto/fileInfo';
import { defaultBucketPath, Globals } from '../globals/globals';
import { Observable, switchMap } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  constructor(private httpClient: HttpClient, private globals: Globals) {}

  // Ask your backend for a presigned URL (adapt endpoint/payload)
  getPresignedUploadUrl(file: File): Observable<FileInfo> {
    return this.httpClient.post<FileInfo>('/api/uploads/presign', {
      fileName: file.name,
      contentType: file.type,
      size: file.size,
    });
  }

  // Perform the upload to the presigned URL; returns raw HttpEvent stream so caller can track progress
  uploadToPresignedUrl(file: File, url: string, method = 'PUT'): Observable<HttpEvent<any>> {
    const headers = new HttpHeaders({ 'Content-Type': file.type || 'application/octet-stream' });
    // use HttpClient.request so we can pass a dynamic method
    return this.httpClient.request(method, url, {
      body: file,
      headers,
      reportProgress: true,
      observe: 'events',
      responseType: 'text' as 'json',
    });
  }

  // Convenience: get presigned URL then upload -> returns the HttpEvent stream
  uploadFileUsingPresign(file: File): Observable<HttpEvent<any>> {
    return this.getPresignedUploadUrl(file).pipe(
      switchMap((info) => this.uploadToPresignedUrl(file, info.presignedUrl, (info as any).method || 'PUT'))
    );
  }
}
