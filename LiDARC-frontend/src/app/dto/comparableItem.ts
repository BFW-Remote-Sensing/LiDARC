import { FileMetadataDTO } from "./fileMetadata";
import { FolderFilesDTO } from "./folderFiles";

export type ComparableItemDTO = FileMetadataDTO | FolderFilesDTO


export type ComparableListItem = ComparableItemDTO &
{
    name: string;
    type: string
};