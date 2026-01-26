import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { defaultComparisonPath, Globals } from "../globals/globals";
import { catchError, Observable, throwError } from "rxjs";
import { ComparisonDTO, CreateComparison } from "../dto/comparison";
import { ComparisonReport } from "../dto/comparisonReport";
import { CreateReportDto } from '../dto/report';
import { ComparisonResponse } from "../dto/comparisonResponse";

const headers = new HttpHeaders({
  'Content-Type': 'application/json',
  Accept: 'application/json',
});

@Injectable({
  providedIn: 'root',
})
export class ComparisonService {
  constructor(
    private httpClient: HttpClient,
    private globals: Globals,
  ) {
  }

  getComparisonById(id: number): Observable<ComparisonDTO> {
    return this.httpClient.get<ComparisonDTO>(
      this.globals.backendUri + defaultComparisonPath + `/${id}`,
      { headers }
    );
  }

  getComparisonReportsById(id: number, limit?: number): Observable<ComparisonReport[]> {
    return this.httpClient.get<ComparisonReport[]>(
      this.globals.backendUri + defaultComparisonPath + `/${id}/reports?limit=${limit}`,
      { headers }
    );
  }

  getAllComparisons(): Observable<ComparisonDTO[]> {
    return this.httpClient.get<ComparisonDTO[]>(
      this.globals.backendUri + defaultComparisonPath + '/all',
      { headers }
    );
  }

  getPagedComparisons(page: number, size: number, sortBy: string, isAscending: boolean, search: string | null): Observable<ComparisonResponse> {
    let params =
      `?page=${page}` +
      `&size=${size}` +
      `&sortBy=${sortBy}` +
      `&ascending=${isAscending}`;

    if (search && search.trim().length > 0) {
      params += `&search=${encodeURIComponent(search)}`;
    }

    return this.httpClient.get<ComparisonResponse>(
      this.globals.backendUri + defaultComparisonPath + "/paged" + params,
      { headers }
    );
  }

  postComparison(comparison: CreateComparison): Observable<ComparisonDTO> {
    return this.httpClient.post<ComparisonDTO>(
      this.globals.backendUri + defaultComparisonPath,
      comparison,
      { headers }
    ).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Captured error:', error);

        let errorMessage = 'An error occurred while creating the comparison.';

        if (error.error && typeof error.error.message === 'string') {
          errorMessage = error.error.message;
        } else if (typeof error.error === 'string') {
          errorMessage = error.error;
        }

        return throwError(() => new Error(errorMessage));
      })
    );
  }

  deleteComparisonById(id: number): Observable<void> {
    return this.httpClient.delete<void>(
      this.globals.backendUri + defaultComparisonPath + `/${id}`,
      { headers }
    ).pipe(
      catchError((error: HttpErrorResponse) => {
        console.error('Captured error:', error);

        let errorMessage = 'An error occurred while deleting the comparison.';

        if (error.error && typeof error.error.message === 'string') {
          errorMessage = error.error.message;
        } else if (typeof error.error === 'string') {
          errorMessage = error.error;
        }

        return throwError(() => new Error(errorMessage));
      })
    );
  }

  createReport(id: number, report: CreateReportDto, files: File[]): Observable<Blob> {
    const formData = new FormData();
    formData.append(
      'report',
      new Blob([JSON.stringify(report)], { type: 'application/json' })
    );
    files.forEach(file => {
      formData.append('files', file, file.name);
    });

    return this.httpClient.post(
      this.globals.backendUri + defaultComparisonPath + `/${id}/reports`,
      formData,
      { responseType: 'blob' },
    );
  }

  startChunkingResult(id: number, chunkSize: number): Observable<void> {
    const params = new HttpParams()
      .set('chunkSize', String(chunkSize))
    return this.httpClient.post<void>(
      this.globals.backendUri + defaultComparisonPath + `/${id}/chunking`, null, { params }
    );
  }

  pollChunkingResult(id: number): Observable<HttpResponse<any>> {
    return this.httpClient.get<any>(
      this.globals.backendUri + defaultComparisonPath + `/${id}/chunking`, { observe: 'response' }
    );
  }
}
