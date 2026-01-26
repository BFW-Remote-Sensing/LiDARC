import {Component, Inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';

export interface PointFilterDialogData {
  title: string;
  subtitle?: string;
  primaryButtonText?: string;
  secondaryButtonText?: string;
  initialLowerBound?: number | null;
  initialUpperBound?: number | null;
}

export interface PointFilterResult {
  lowerBound: number;
  upperBound: number;
}

@Component({
  selector: 'app-point-filter-dialogue',
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    FormsModule
  ],
  templateUrl: './point-filter-dialogue.html',
  styleUrl: './point-filter-dialogue.scss',
})
export class PointFilterDialogue {
  lowerBound: number | null = null;
  upperBound: number | null = null;
  errorMessage: string = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PointFilterDialogData,
    private dialogRef: MatDialogRef<PointFilterDialogue>
  ) {
    if (data.initialLowerBound !== null && data.initialLowerBound !== undefined) {
      this.lowerBound = data.initialLowerBound;
    }
    if (data.initialUpperBound !== null && data.initialUpperBound !== undefined) {
      this.upperBound = data.initialUpperBound;
    }
  }

  validateBounds(): boolean {
    this.errorMessage = '';

    if (this.lowerBound === null || this.lowerBound === undefined) {
      this.errorMessage = 'Please enter a lower bound value';
      return false;
    }

    if (this.upperBound === null || this.upperBound === undefined) {
      this.errorMessage = 'Please enter an upper bound value';
      return false;
    }

    const lower = Number(this.lowerBound);
    const upper = Number(this.upperBound);

    if (isNaN(lower) || isNaN(upper)) {
      this.errorMessage = 'Please enter valid numbers';
      return false;
    }

    if (lower < 0 || lower > 100) {
      this.errorMessage = 'Lower bound must be between 0.00 and 100.00';
      return false;
    }

    if (upper < 0 || upper > 100) {
      this.errorMessage = 'Upper bound must be between 0.00 and 100.00';
      return false;
    }

    if (lower >= upper) {
      this.errorMessage = 'Lower bound must be less than upper bound';
      return false;
    }

    return true;
  }

  canConfirm(): boolean {
    return this.lowerBound !== null &&
           this.upperBound !== null &&
           this.lowerBound >= 0 &&
           this.lowerBound <= 100 &&
           this.upperBound >= 0 &&
           this.upperBound <= 100 &&
           this.lowerBound < this.upperBound;
  }

  onConfirm() {
    if (this.validateBounds()) {
      const result: PointFilterResult = {
        lowerBound: Math.round(Number(this.lowerBound) * 100) / 100,
        upperBound: Math.round(Number(this.upperBound) * 100) / 100
      };
      this.dialogRef.close(result);
    }
  }

  onCancel() {
    this.dialogRef.close(null);
  }

  onClear() {
    this.lowerBound = null;
    this.upperBound = null;
    this.errorMessage = '';
    this.dialogRef.close({ cleared: true });
  }
}

