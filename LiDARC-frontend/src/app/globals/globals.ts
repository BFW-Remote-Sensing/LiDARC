import { Injectable, isDevMode } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class Globals {
  readonly backendUri: string = 'http://localhost:' + backendPort + '/api/v1';
}
const debug: boolean = isDevMode(); //to see network requests locally during debug set to debug true or start with --configuration=development
const backendPort: string = debug ? '8080' : '8081'; //8081 so that it routes differently on CI when forwarding

export const headers = new HttpHeaders({
  'Content-Type': 'application/json',
  Accept: 'application/json',
});

export const defaultBucketPath: string = '/bucket';
export const defaultMetadataPath: string = '/metadata';
export const defaultComparisonPath: string = '/comparisons';
export const defaultFolderPath: string = '/folders';

export const pollingIntervalMs: number = 1000;
export const snackBarDurationMs: number = 3000;

export const httpOptions = {
  headers: new HttpHeaders({
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }),
};
