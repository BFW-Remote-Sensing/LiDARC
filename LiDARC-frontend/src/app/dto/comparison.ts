import { FileMetadataDTO } from "./fileMetadata";

export type CreateComparison = {
    name: string;
    needHighestVegetation: boolean;
    needOutlierDetection: boolean;
    needStatisticsOverScenery: boolean;
    needMostDifferences: boolean;
    fileMetadataIds: number[];
    //grid: any; // TODO
}

export type ComparisonDTO = CreateComparison & {
    id: number;
    createdAt: string;
    status: string;
    resultReportUrl: string | null;
    errorMessage: string | null;
    files: FileMetadataDTO[];
}