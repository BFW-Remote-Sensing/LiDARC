import {HttpClient, HttpHeaders, HttpParams} from "@angular/common/http";
import {Injectable} from "@angular/core";
import {Globals} from "../globals/globals";
import {Observable} from 'rxjs';
import {ComparisonReport} from '../dto/comparisonReport';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const headers = new HttpHeaders({
  'Content-Type': 'application/json',
  Accept: 'application/json',
});

@Injectable({
  providedIn: 'root',
})
export class ReportSerivce {
  constructor(
    private httpClient: HttpClient,
    private globals: Globals,
  ) {
  }


  getAllReports(
    pageIndex: number = 0,
    pageSize: number = 20,
    sortField: string = 'creationDate',
    sortDir: string = 'desc',
    search: string = ''
  ): Observable<Page<ComparisonReport>> {
    let params = new HttpParams()
      .set('page', pageIndex)
      .set('size', pageSize)
      .set('sort', `${sortField},${sortDir}`);

    if (search) {
      params = params.set('search', search);
    }

    return this.httpClient.get<Page<ComparisonReport>>(
      this.globals.backendUri + '/reports',
      {params}
    );
  }
}
