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

    getAllMetadata(): Observable<FileMetadataDTO[]> {
        return this.httpClient.get<FileMetadataDTO[]>(
           this.globals.backendUri + defaultMetadataPath + '/all-metadata',
            { headers }
        );
    }
}