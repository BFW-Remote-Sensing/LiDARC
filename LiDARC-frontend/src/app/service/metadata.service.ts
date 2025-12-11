import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { defaultMetadataPath, Globals } from "../globals/globals";
import { Observable } from "rxjs";
import { FileMetadataDTO } from "../dto/fileMetadata";

const headers = new HttpHeaders({
    'Content-Type': 'application/json',
    Accept: 'application/json',
});

@Injectable({
    providedIn: 'root',
})
export class MetadataService {
    constructor(
        private httpClient: HttpClient,
        private globals: Globals,
    ) { }

    getMetadataById(id: number): Observable<FileMetadataDTO> {
        return this.httpClient.get<FileMetadataDTO>(
            this.globals.backendUri + defaultMetadataPath + `/${id}`,
            { headers }
        );
    }

    getAllMetadata(): Observable<FileMetadataDTO[]> {
        return this.httpClient.get<FileMetadataDTO[]>(
            this.globals.backendUri + defaultMetadataPath + '/all',
            { headers }
        );
    }

    getPagedMetadata(page: number, size: number, sortBy: string, isAscending: boolean): Observable<FileMetadataDTO[]> {
        return this.httpClient.get<FileMetadataDTO[]>(
            this.globals.backendUri + defaultMetadataPath + `?page=${page}&size=${size}&sortBy=${sortBy}&ascending=${isAscending}`,
            { headers }
        );
    }

    deleteMetadataById(id: number): Observable<void> {
        return this.httpClient.delete<void>(
            this.globals.backendUri + defaultMetadataPath + `/${id}`,
            { headers }
        );
    }
}