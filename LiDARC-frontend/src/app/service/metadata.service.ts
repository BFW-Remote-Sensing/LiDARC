import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { defaultMetadataPath, Globals, headers } from "../globals/globals";
import { Observable, Subject } from "rxjs";
import { FileMetadataDTO } from "../dto/fileMetadata";
import { MetadataResponse } from "../dto/metadataResponse";
import { FolderFilesDTO } from "../dto/folderFiles";
import { ComparableResponse } from "../dto/comparableResponse";

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

    getAllMetadataWithoutFolder(): Observable<FileMetadataDTO[]> {
        return this.httpClient.get<FileMetadataDTO[]>(
            this.globals.backendUri + defaultMetadataPath + '/unassigned/all',
            { headers }
        );
    }

    getPagedMetadataWithoutFolder(page: number, size: number, sortBy: string, isAscending: boolean, search: string | null): Observable<MetadataResponse> {
        let params =
            `?page=${page}` +
            `&size=${size}` +
            `&sortBy=${sortBy}` +
            `&ascending=${isAscending}`;

        if (search && search.trim().length > 0) {
            params += `&search=${encodeURIComponent(search)}`;
        }

        return this.httpClient.get<MetadataResponse>(
            this.globals.backendUri + defaultMetadataPath + '/unassigned/paged' + params,
            { headers }
        );
    }

    getMetadataGroupedByFolder(): Observable<FolderFilesDTO[]> {
        return this.httpClient.get<FolderFilesDTO[]>(
            this.globals.backendUri + defaultMetadataPath + '/assigned/grouped-by-folder/all',
            { headers }
        );
    }

    getAllMetadataGroupedByFolderPaged(page: number, size: number, search: string | null): Observable<ComparableResponse> {
        let params = `?page=${page}&size=${size}`;
        if (search && search.trim().length > 0) {
            params += `&search=${encodeURIComponent(search)}`;
        }
        return this.httpClient.get<ComparableResponse>(
            this.globals.backendUri + defaultMetadataPath + '/all/grouped-by-folder/paged' + params,
            { headers }
        );
    }

    deleteMetadataById(id: number): Observable<void> {
        return this.httpClient.delete<void>(
            this.globals.backendUri + defaultMetadataPath + `/${id}`,
            { headers }
        );
    }

    assignFolder(metadataIds: number[], folderId: number): Observable<void> {
        const params = new HttpParams().set('folderId', folderId.toString());
        return this.httpClient.put<void>(
            `${this.globals.backendUri + defaultMetadataPath}/assign-folder`,
            metadataIds,
            { params }
        );
    }
}