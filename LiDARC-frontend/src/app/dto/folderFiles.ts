import { FileMetadataDTO } from "./fileMetadata";

export interface FolderFilesDTO {
    id: number;
    folderName: string;
    createdDate: Date;
    status: string;
    files: FileMetadataDTO[];
}