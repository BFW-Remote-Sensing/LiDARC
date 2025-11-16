import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpHeaders, HttpRequest } from '@angular/common/http';
import { FileInfo } from '../dto/fileInfo';
import { defaultBucketPath, Globals } from '../globals/globals';
import { Observable, switchMap, throwError } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  constructor(private httpClient: HttpClient, private globals: Globals) {}

  // Ask your backend for a presigned URL (adapt endpoint/payload)
  getPresignedUploadUrl(file: File): Observable<FileInfo> {
    console.log('sending presign requeset for file ' + file.name);
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      Accept: 'application/json',
    });
    const payload: FileInfo = {
      fileName: file.name,
      method: 'POST',
    };
    return this.httpClient.post<FileInfo>(
      this.globals.backendUri + defaultBucketPath + '/upload',
      payload,
      { headers }
    );
  }

  // Perform the upload to the presigned URL; returns raw HttpEvent stream so caller can track progress
  uploadToPresignedUrl(file: File, url: string, method = 'PUT'): Observable<HttpEvent<any>> {
    const headers = new HttpHeaders({ 'Content-Type': 'multipart/form-data' });
    var formData = new FormData();
    formData.append('file', file);

    // use HttpClient.request so we can pass a dynamic method
    console.log('uploading file ' + file.name + ' to ' + url + ' with ' + headers);
    return this.httpClient.put(url, formData, {
      headers: headers,
      reportProgress: true,
      observe: 'events',
      withCredentials: false,
    });
  }

  // Convenience: get presigned URL then upload -> returns the HttpEvent stream
  uploadFileUsingPresign(file: File): Observable<HttpEvent<any>> {
    return this.getPresignedUploadUrl(file).pipe(
      switchMap((info) => {
        console.log(info);
        if (!info || !info.presignedURL) {
          return throwError(() => new Error('Presign request failed: missing presignedUrl'));
        }
        return this.uploadToPresignedUrl(file, info.presignedURL, 'POST');
      })
    );
  }
}
