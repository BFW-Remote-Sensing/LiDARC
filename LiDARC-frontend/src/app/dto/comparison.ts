import { FileMetadataDTO } from "./fileMetadata";

export type CreateComparison = {
    name: string;
    needHighestVegetation: boolean;
    needOutlierDetection: boolean;
    needStatisticsOverScenery: boolean;
    needMostDifferences: boolean;
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
}
