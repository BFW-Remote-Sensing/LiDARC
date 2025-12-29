import { ComparableItemDTO } from "./comparableItem";

export interface ComparableResponse {
    totalItems: number;
    items: ComparableItemDTO[];
}