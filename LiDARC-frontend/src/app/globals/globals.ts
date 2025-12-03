import { Injectable } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class Globals {
  readonly backendUri: string = 'http://localhost:8080/api/v1';
}

export const defaultBucketPath: string = '/bucket';
export const defaultMetadataPath: string = '/metadata';

export const httpOptions = {
  headers: new HttpHeaders({
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }),
};
