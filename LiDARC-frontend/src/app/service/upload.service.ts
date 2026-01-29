import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { FileInfo } from '../dto/fileInfo';
import { defaultBucketPath, defaultFolderPath, Globals, headers } from '../globals/globals';
import { catchError, from, map, Observable, switchMap, throwError } from 'rxjs';
import { CreateEmptyFolder } from '../dto/createEmptyFolder';
import { UploadFile } from '../entity/UploadFile';

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  constructor(
    private httpClient: HttpClient,
    private globals: Globals,
  ) {
  }

  // Ask your backend for a presigned URL (adapt endpoint/payload)
  getPresignedUploadUrl(file: File, hash: string, folderId?: number): Observable<FileInfo> {
    console.log('sending presign request for file ' + file.name);
    const payload: FileInfo = {
      fileName: hash + '_' + file.name,
      originalFileName: file.name,
      folderId: folderId ?? null
    };
    return this.httpClient.post<FileInfo>(
      this.globals.backendUri + defaultBucketPath + '/upload',
      payload,
      { headers }
    );
  }

  getEmptyFolder(EmptyFolderName: string): Observable<CreateEmptyFolder> {
    console.log('sending presign request for empty folder ' + EmptyFolderName);
    const payload: CreateEmptyFolder = {
      name: EmptyFolderName,
      status: 'UPLOADING'
    };
    return this.httpClient.post<CreateEmptyFolder>(
      this.globals.backendUri + defaultFolderPath + '/empty',
      payload,
      { headers }
    );
  }

  uploadToPresignedUrl(file: File, url: string, method = 'PUT'): Observable<HttpEvent<any>> {

    return new Observable(observer => {
      const reader = new FileReader();

      reader.onload = () => {
        const parsed = new URL(url);
        let proxiedUrl = url;
        if (parsed.hostname === 'minio') {
          proxiedUrl = this.globals.toMinioProxyUrl(url);
        }

        console.log('Uploading to presigned URL:', proxiedUrl);

        this.httpClient.request(method, proxiedUrl, {
          body: reader.result,        // file content AFTER load finishes
          reportProgress: true,
          observe: 'events',
          withCredentials: false
        })
          .subscribe({
            next: e => observer.next(e),
            error: err => observer.error(err),
            complete: () => observer.complete()
          });
      };

      reader.onerror = err => observer.error(err);

      // Start reading the file (async)
      reader.readAsArrayBuffer(file);  // better than readAsText for uploads
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
        return this.getPresignedUploadUrl(file, hash, uploadFile.folderId).pipe(
          catchError((err) => {
            console.error('getPresignedUploadUrl failed for file ' + file.name, err);
            return throwError(() => err);
          })
        );
      }),
      switchMap((info) => {
        if (!info || !info.presignedURL) {
          const error = new Error('Presign request failed: missing presignedUrl');
          console.error('Presign validation failed for file ' + file.name, error);
          return throwError(() => error);
        }
        return this.uploadToPresignedUrl(file, info.presignedURL, 'PUT').pipe(
          catchError((err) => {
            console.error('uploadToPresignedUrl failed for file ' + file.name, err);
            return throwError(() => err);
          })
        );
      })
    );
  }

  markFolderComplete(folderId: number) {
    return this.httpClient.put<void>(this.globals.backendUri + defaultFolderPath, {
      id: folderId,
      status: 'UPLOADED'
    });
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
