import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { catchError, Observable, throwError } from "rxjs";
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

    getFoldersWithoutComparison(): Observable<FolderDTO[]> {
        return this.httpClient.get<FolderDTO[]>(
            this.globals.backendUri + defaultFolderPath + '/actives-without-comparison',
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

    deleteFolderById(id: number): Observable<void> {
        return this.httpClient.delete<void>(
            this.globals.backendUri + defaultFolderPath + `/${id}`,
            { headers }
        ).pipe(
            catchError((error: HttpErrorResponse) => {
                console.error('Captured error:', error);

                let errorMessage = 'An error occurred while deleting the folder.';

                if (error.error && typeof error.error.message === 'string') {
                    errorMessage = error.error.message;
                } else if (typeof error.error === 'string') {
                    errorMessage = error.error;
                }

                return throwError(() => new Error(errorMessage));
            })
        );
    }
}