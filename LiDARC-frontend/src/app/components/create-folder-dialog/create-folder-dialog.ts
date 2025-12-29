import { Component, inject, Inject, signal, WritableSignal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from "@angular/forms";
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CreateFolderDTO } from '../../dto/createFolder';
import { FolderService } from '../../service/folderService.service';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-create-folder-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinner,
  ],
  templateUrl: './create-folder-dialog.html',
  styleUrl: './create-folder-dialog.scss',
})
export class CreateFolderDialog {
  folderService = inject(FolderService);
  public loading: WritableSignal<boolean> = signal(false);

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: FileMetadataDTO[],
    private dialogRef: MatDialogRef<CreateFolderDialog>
  ) { }

  folder: CreateFolderDTO = {
    name: '',
    status: '',
    fileIds: []
  }

  onCreate() {
    this.folder.fileIds = this.data.map(file => file.id);
    this.folder.status = this.data.some(file => file.status !== 'PROCESSED') ? 'PROCESSING' : 'PROCESSED';
    this.folderService.postFolder(this.folder).subscribe({
      next: (folder) => {
        this.dialogRef.close(folder);
      },
      error: (error) => {
        console.error('Error creating folder:', error);
        alert("Error while creating folder.");
      }
    });
  }

  onCancel() {
    this.dialogRef.close(false); // return false when cancelled
  }
}
