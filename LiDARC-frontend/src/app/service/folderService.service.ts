import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { Globals, defaultFolderPath, headers } from "../globals/globals";
import { FolderFilesDTO } from "../dto/folderFiles";
import { CreateFolderDTO } from "../dto/createFolder";
import { Folder } from "../entity/Folder";
import { FolderDTO } from "../dto/folder";

@Injectable({
    providedIn: 'root',
})
export class FolderService {
    constructor(
        private httpClient: HttpClient,
        private globals: Globals,
    ) { }

    getFolderById(id: number): Observable<FolderFilesDTO> {
        return this.httpClient.get<FolderFilesDTO>(
            this.globals.backendUri + defaultFolderPath + `/${id}`,
            { headers }
        );
    }

    getFolders(): Observable<FolderDTO[]> {
        return this.httpClient.get<FolderDTO[]>(
            this.globals.backendUri + defaultFolderPath + '/all',
            { headers }
        );
    }

    postFolder(newFolder: CreateFolderDTO): Observable<Folder> {
        return this.httpClient.post<Folder>(
            this.globals.backendUri + defaultFolderPath,
            newFolder,
            { headers }
        );
    }
}