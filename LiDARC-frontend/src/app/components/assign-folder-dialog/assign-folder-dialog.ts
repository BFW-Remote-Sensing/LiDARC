import { Component, inject, Inject, signal, WritableSignal } from '@angular/core';
import { FolderService } from '../../service/folderService.service';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { FolderDTO } from '../../dto/folder';
import { MetadataService } from '../../service/metadata.service';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatHint, MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-assign-folder-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule
  ],
  templateUrl: './assign-folder-dialog.html',
  styleUrl: './assign-folder-dialog.scss',
})
export class AssignFolderDialog {
  folderService = inject(FolderService);
  fileService = inject(MetadataService);
  public loadingFolders: WritableSignal<boolean> = signal(true);
  public errorFolders: WritableSignal<string | null> = signal(null);

  public loadingAssign: WritableSignal<boolean> = signal(false);
  public errorAssign: WritableSignal<string | null> = signal(null);

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: number[],
    private dialogRef: MatDialogRef<AssignFolderDialog>,
    private router: Router
  ) { }

  folders: WritableSignal<FolderDTO[]> = signal([]);
  filteredFolders: WritableSignal<FolderDTO[]> = signal([]);
  selectedFolderId: WritableSignal<number | null> = signal(null);

  ngOnInit() {
    this.folderService.getFolders().subscribe({
      next: (folders) => {
        console.log('Loaded folders:', folders);
        this.folders.set(folders);
        this.filteredFolders.set(folders);
        this.loadingFolders.set(false);
      },
      error: (error) => {
        console.error('Error loading folders:', error);
        this.loadingFolders.set(false);
      }
    });
  }

  filterFolders(query: string): void {
    const lowerQuery = query.toLowerCase();
    console.log('Filtering folders with query:', query);
    const filtered = this.folders().filter(folder => folder.name.toLowerCase().includes(lowerQuery));
    this.filteredFolders.set(filtered);
  }

  onAssign() {
    this.loadingAssign.set(true);
    this.errorAssign.set(null);
    const fileIds = this.data;
    if (this.selectedFolderId() !== null) {
      this.fileService.assignFolder(fileIds, this.selectedFolderId()!).subscribe({
        next: () => {
          this.dialogRef.close();
          this.router.navigate(['/folders', this.selectedFolderId()]);
        },
        error: (error) => {
          console.error('Error assigning to folder:', error);
          alert("Error while assigning to folder.");
        }
      });
    }
  }

  onCancel() {
    this.dialogRef.close(false); // return false when cancelled
  }
}
