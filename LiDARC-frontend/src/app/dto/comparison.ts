import {FileMetadataDTO} from "./fileMetadata";
import {FolderDTO} from "./folder";

export type CreateComparison = {
    name: string;
    needOutlierDetection: boolean;
    individualStatisticsPercentile: number | null;
    folderAId?: number;
    folderBId?: number;
    folderAFiles: number[];
    folderBFiles: number[];
    grid: GridParameters | null;
    pointFilterLowerBound?: number | null;
    pointFilterUpperBound?: number | null;
    needPointFilter?: boolean;
    outlierDeviationFactor?: number;
}

export type GridParameters = {
    cellWidth: number;
    cellHeight: number;
    xMin: number;
    xMax: number;
    yMin: number;
    yMax: number;
}

export type ComparisonDTO = CreateComparison & {
    id: number;
    createdAt: string;
    status: string;
    latestReport: string | null;
    errorMessage: string | null;
    files: FileMetadataDTO[];
    folderA?: FolderDTO;
    folderB?: FolderDTO;
}
