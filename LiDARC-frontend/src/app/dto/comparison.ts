import { FileMetadataDTO } from "./fileMetadata";
import { FolderDTO } from "./folder";

export type CreateComparison = {
    name: string;
    needHighestVegetation: boolean;
    needOutlierDetection: boolean;
    needStatisticsOverScenery: boolean;
    needMostDifferences: boolean;
    individualStatisticsPercentile: number | null;
    folderAId?: number;
    folderBId?: number;
    folderAFiles: number[];
    folderBFiles: number[];
    grid: GridParameters | null;
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
