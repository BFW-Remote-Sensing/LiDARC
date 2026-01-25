export interface FileMetadataDTO {
    id: number;
    filename: string;
    originalFilename: string;
    captureYear?: number;
    sizeBytes: number;
    minX: number;
    minY: number;
    minZ: number;
    maxX: number;
    maxY: number;
    maxZ: number;
    status: string;
    systemIdentifier?: string;
    lasVersion?: string;
    captureSoftware?: string;
    uploaded?: boolean;
    fileCreationDate?: string;
    pointCount?: number;
    uploadedAt?: string;
    type?: string;
    folderId?: number;
    errorMessage?: string;
    active?: boolean;
}