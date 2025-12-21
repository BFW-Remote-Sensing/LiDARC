import { FileMetadataDTO } from "./fileMetadata";

export interface MetadataResponse {
    items: FileMetadataDTO[];
    totalItems: number;
    page: number;
    size: number;
}