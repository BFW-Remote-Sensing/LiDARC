import { Component, Inject, OnInit, signal, WritableSignal, inject, Input } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialogModule } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { FileMetadataDTO } from '../dto/fileMetadata';

@Component({
  selector: 'app-grid-definition-dialog',
  templateUrl: 'define-grid.html',
  styleUrl: 'define-grid.scss',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatDialogModule
  ]
})
export class GridDefinitionDialogComponent implements OnInit {
  cellWidth: number = 1;
  cellHeight: number = 1;


  minX: number | null = null;
  maxX: number | null = null;
  minY: number | null = null;
  maxY: number | null = null;
  file!: FileMetadataDTO;
  constructor(
    public dialogRef: MatDialogRef<GridDefinitionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {file: FileMetadataDTO}
  ) {
  }


  ngOnInit() {
    console.log('File passed to dialog:', this.file);
    this.file = this.data.file;
    this.minX = this.file.minX;
    this.maxX = this.file.maxX;
    this.minY = this.file.minY;
    this.maxY = this.file.maxY;
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onDefine(): void {
    if (this.cellWidth != null && this.cellHeight != null &&
      this.minX !== null && this.maxX !== null &&
      this.minY !== null && this.maxY !== null) {
      this.dialogRef.close({
        cellWidth: this.cellWidth,
        cellHeight: this.cellHeight,
        minX: this.minX,
        maxX: this.maxX,
        minY: this.minY,
        maxY: this.maxY
      });
    }
  }
}
