// confirmation-dialog.component.ts
import { Component, Inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmationDialogData {
  title: string;
  onConfirm: () => Observable<any>;
  subtitle?: string;
  objectName?: string;
  primaryButtonText?: string;
  secondaryButtonText?: string;
  primaryButtonColor?: 'primary' | 'warn' | 'accent';
  successActionText?: string;
  extensionMessage?: string;
}

@Component({
  selector: 'app-confirmation-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    CommonModule,
    MatIconModule
  ],
  templateUrl: './confirmation-dialog.html',
  styleUrls: ['./confirmation-dialog.scss'],
})
export class ConfirmationDialogComponent {
  loading = signal<boolean>(false);
  isSuccess = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ConfirmationDialogData,
    private dialogRef: MatDialogRef<ConfirmationDialogComponent>
  ) { }

  onConfirm() {
    this.errorMessage.set(null);
    this.loading.set(true);

    this.data.onConfirm().subscribe({
      next: (response) => {
        this.loading.set(false);
        this.isSuccess.set(true);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(error.message || 'An error occurred during confirmation.');
      }
    });
  }

  onCancel() {
    if (!this.loading()) {
      this.dialogRef.close(false);
    }
  }

  onCloseSuccess() {
    this.dialogRef.close(true);
  }
}