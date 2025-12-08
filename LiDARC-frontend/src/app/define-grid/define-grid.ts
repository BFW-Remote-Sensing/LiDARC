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


  xMin: number | null = null;
  xMax: number | null = null;
  yMin: number | null = null;
  yMax: number | null = null;
  file!: FileMetadataDTO;
  constructor(
    public dialogRef: MatDialogRef<GridDefinitionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {file: FileMetadataDTO}
  ) {
  }


  ngOnInit() {
    console.log('File passed to dialog:', this.file);
    this.file = this.data.file;
    this.xMin = this.file.minX;
    this.xMax = this.file.maxX;
    this.yMin = this.file.minY;
    this.yMax = this.file.maxY;
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onDefine(): void {
    if (this.cellWidth != null && this.cellHeight != null &&
      this.xMin !== null && this.xMax !== null &&
      this.yMin !== null && this.yMax !== null) {
      this.dialogRef.close({
        cellWidth: this.cellWidth,
        cellHeight: this.cellHeight,
        xMin: this.xMin,
        xMax: this.xMax,
        yMin: this.yMin,
        yMax: this.yMax
      });
    }
  }
}
