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
      .set('chunkSize', String(chunkSize));
    return this.httpClient.post<void>(
      this.globals.backendUri + defaultComparisonPath + `/${id}/chunking`, null, { params }
    );
  }

  pollChunkingResult(id: number, chunkSize: number): Observable<HttpResponse<any>> {
    const params = new HttpParams()
      .set('chunkSize', String(chunkSize));
    return this.httpClient.get<any>(
      this.globals.backendUri + defaultComparisonPath + `/${id}/chunking`, { params, observe: 'response' }
    );
  }

  /**
   * Creates an EventSource for SSE streaming of chunking results.
   * Returns an Observable that emits the chunking result when received.
   * Results are cached per chunkSize, so the same chunkSize must be used.
   * Handles streaming of large results in chunks for memory efficiency.
   */
  streamChunkingResult(id: number, chunkSize: number): Observable<any> {
    return new Observable(observer => {
      const url = this.globals.backendUri + defaultComparisonPath + `/${id}/chunking/stream?chunkSize=${chunkSize}`;
      const eventSource = new EventSource(url);

      // Buffer for accumulating streamed chunks
      let chunks: string[] = [];
      let totalChunks = 0;
      let isStreaming = false;

      // Handle legacy single-event response (for backwards compatibility)
      eventSource.addEventListener('chunking-result', (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          observer.next(data);
          observer.complete();
          eventSource.close();
        } catch (e) {
          observer.error(e);
          eventSource.close();
        }
      });

      // Handle streaming start event
      eventSource.addEventListener('chunking-result-start', (event: MessageEvent) => {
        try {
          const metadata = JSON.parse(event.data);
          totalChunks = metadata.totalChunks || 0;
          chunks = new Array(totalChunks);
          isStreaming = true;
          console.log(`Starting to receive ${totalChunks} chunks, total size: ${metadata.totalSize} bytes`);
        } catch (e) {
          console.error('Error parsing streaming start event:', e);
        }
      });

      // Handle streaming chunk events
      eventSource.addEventListener('chunking-result-chunk', (event: MessageEvent) => {
        if (isStreaming && event.lastEventId) {
          const chunkIndex = parseInt(event.lastEventId, 10);
          chunks[chunkIndex] = event.data;
        }
      });

      // Handle streaming end event - reassemble and parse the complete JSON
      eventSource.addEventListener('chunking-result-end', (event: MessageEvent) => {
        try {
          if (isStreaming) {
            // Reassemble the complete JSON from chunks
            const completeJson = chunks.join('');
            const data = JSON.parse(completeJson);
            observer.next(data);
            observer.complete();
            console.log('Successfully received and parsed streamed chunking result');
          }
          eventSource.close();
        } catch (e) {
          console.error('Error parsing reassembled streaming data:', e);
          observer.error(e);
          eventSource.close();
        }
      });

      eventSource.onerror = (error) => {
        observer.error(error);
        eventSource.close();
      };

      // Cleanup on unsubscribe
      return () => {
        eventSource.close();
      };
    });
  }
}
