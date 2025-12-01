import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpHeaders, HttpRequest } from '@angular/common/http';
import { FileInfo } from '../dto/fileInfo';
import { defaultBucketPath, Globals } from '../globals/globals';
import { Observable, switchMap, throwError, from, map, catchError, tap } from 'rxjs';
import { HttpEventType, HttpResponse } from '@angular/common/http';

const headers = new HttpHeaders({
  'Content-Type': 'application/json',
  Accept: 'application/json',
});

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  constructor(
    private httpClient: HttpClient,
    private globals: Globals,
  ) {}

  // Ask your backend for a presigned URL (adapt endpoint/payload)
  getPresignedUploadUrl(file: File, hash: string): Observable<FileInfo> {
    console.log('sending presign request for file ' + file.name);
    const payload: FileInfo = {
      fileName: hash + '_' + file.name,
      originalFileName: file.name,
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
    console.log('Uploading to presigned URL: ' + url);
    const parsed = new URL(url);
    const proxiedUrl = `http://localhost:8081/minio-upload${parsed.pathname}${parsed.search}`;

    // use HttpClient.request so we can pass a dynamic method
    console.log('uploading file ' + file.name + ' to ' + url + ' with ' + headers);
    return this.httpClient.put(proxiedUrl, formData, {
      //headers: headers,
      reportProgress: true,
      observe: 'events',
      withCredentials: false,
    });
  }

  onComplete?(file: File, hash: string) {
    // callback to signal to backend that upload is complete
    const payload: FileInfo = {
      fileName: hash + '_' + file.name,
      originalFileName: file.name,
    };
    return this.httpClient.put<FileInfo>(
      this.globals.backendUri + defaultBucketPath + '/upload',
      payload,
      { headers }
    );
  }

  uploadFileUsingPresign(file: File, uploadFile: UploadFile): Observable<HttpEvent<any>> {
    return this.hashFile(file).pipe(
      // if hashing fails, the error will propagate
      switchMap((hash) => {
        uploadFile.hash = hash;
        console.log('computed hash for file ' + file.name + ': ' + hash);
        return this.getPresignedUploadUrl(file, hash);
      }),
      switchMap((info) => {
        if (!info || !info.presignedURL) {
          return throwError(() => new Error('Presign request failed: missing presignedUrl'));
        }
        return this.uploadToPresignedUrl(file, info.presignedURL, 'PUT');
      })
    );
  }

  // compute SHA-256 of a File and return Observable<string> (hex)
  private hashFile(file: File): Observable<string> {
    // from(file.arrayBuffer()) converts the Promise to Observable
    return from(file.arrayBuffer()).pipe(
      switchMap((buffer) => from(crypto.subtle.digest('SHA-256', buffer))),
      map((hashBuffer) => {
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
      }),
      catchError((err) => {
        console.error('hashFile failed', err);
        return throwError(() => err);
      })
    );
  }
}
