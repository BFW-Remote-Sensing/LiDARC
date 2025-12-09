// confirmation-dialog.component.ts
import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ConfirmationDialogData {
  title: string;
  subtitle?: string;
  primaryButtonText?: string;
  secondaryButtonText?: string;
}

@Component({
  selector: 'app-confirmation-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.subtitle }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ data.secondaryButtonText || 'Cancel' }}</button>
      <button mat-raised-button color="primary" (click)="onConfirm()">
        {{ data.primaryButtonText || 'Confirm' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmationDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ConfirmationDialogData,
    private dialogRef: MatDialogRef<ConfirmationDialogComponent>
  ) { }

  onConfirm() {
    this.dialogRef.close(true); // return true when confirmed
  }

  onCancel() {
    this.dialogRef.close(false); // return false when cancelled
  }
}
