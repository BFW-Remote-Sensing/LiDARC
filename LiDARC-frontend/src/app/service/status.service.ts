import { Injectable } from "@angular/core";

@Injectable({
    providedIn: 'root'
})
export class StatusService {
    getComparableSnackbarMessage(objectType: string, objectName: string, newStatus: string): string {
        if (newStatus === 'UPLOADED') {
            return `${objectType} "${objectName}" has been uploaded successfully.`;
        } else if (newStatus === 'PROCESSING') {
            return `${objectType} "${objectName}" is now being processed.`;
        } else if (newStatus === 'PROCESSED') {
            return `${objectType} "${objectName}" has been processed successfully.`;
        } else if (newStatus === 'FAILED') {
            return `${objectType} "${objectName}" has failed.`;
        }
        return "";
    }

    getComparisonSnackbarMessage(objectType: string, objectName: string, newStatus: string): string {
        if (newStatus === 'COMPARING') {
            return `${objectType} "${objectName}" is now being compared.`;
        } else if (newStatus === 'COMPLETED') {
            return `${objectType} "${objectName}" has been completed successfully.`;
        } else if (newStatus === 'FAILED') {
            return `${objectType} "${objectName}" has failed.`;
        }
        return "";
    }
}