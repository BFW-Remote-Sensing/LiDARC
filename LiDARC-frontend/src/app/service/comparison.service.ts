import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { defaultComparisonPath, Globals, headers } from "../globals/globals";
import { Observable } from "rxjs";
import { ComparisonDTO, CreateComparison } from "../dto/comparison";
import { ComparisonReport } from "../dto/comparisonReport";
import { CreateReportDto } from '../dto/report';

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

    getComparisonReportsById(id: number): Observable<ComparisonReport[]> {
        return this.httpClient.get<ComparisonReport[]>(
            this.globals.backendUri + defaultComparisonPath + `/${id}/reports`,
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
}
