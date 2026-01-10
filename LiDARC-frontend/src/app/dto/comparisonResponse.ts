import { ComparisonDTO } from "./comparison";

export interface ComparisonResponse {
    items: ComparisonDTO[];
    totalItems: number;
    page: number;
    size: number;
}