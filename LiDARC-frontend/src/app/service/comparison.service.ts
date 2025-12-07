import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { defaultComparisonPath, Globals } from "../globals/globals";
import { Observable } from "rxjs";
import { ComparisonDTO, CreateComparison } from "../dto/comparison";

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
    ) { }

    getComparisonById(id: number): Observable<ComparisonDTO> {
        return this.httpClient.get<ComparisonDTO>(
            this.globals.backendUri + defaultComparisonPath + `/${id}`,
            { headers }
        );
    }

    getAllComparisons(): Observable<ComparisonDTO[]> {
        return this.httpClient.get<ComparisonDTO[]>(
            this.globals.backendUri + defaultComparisonPath + '/all',
            { headers }
        );
    }

    getPagedComparisons(page: number, size: number, sortBy: string, isAscending: boolean): Observable<ComparisonDTO[]> {
        return this.httpClient.get<ComparisonDTO[]>(
            this.globals.backendUri + defaultComparisonPath + `?page=${page}&size=${size}&sortBy=${sortBy}&ascending=${isAscending}`,
            { headers }
        );
    }

    postComparison(comparison: CreateComparison): Observable<ComparisonDTO> {
        return this.httpClient.post<ComparisonDTO>(
            this.globals.backendUri + defaultComparisonPath,
            comparison,
            { headers }
        );
    }

    deleteComparisonById(id: number): Observable<void> {
        return this.httpClient.delete<void>(
            this.globals.backendUri + defaultComparisonPath + `/${id}`,
            { headers }
        );
    }
}