import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {NgIf} from '@angular/common';

export interface ConfirmationDialogData {
  title: string;
  subtitle?: string;
  primaryButtonText?: string;
  secondaryButtonText?: string;

  showAsFolderOption?: boolean;
  defaultUploadAsFolder?: boolean;
  defaultFolderName?: string;
}

export interface ConfirmationDialogResult {
  confirmed: boolean;
  uploadAsFolder: boolean;
  folderName?: string;
}

@Component({
  selector: 'app-upload-as-folder-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    NgIf,
  ],
  templateUrl: './upload-as-folder-dialogue.html',
})
export class UploadAsFolderDialogComponent {
  form!: FormGroup<{
    uploadAsFolder: FormControl<boolean>;
    folderName: FormControl<string>;
  }>;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ConfirmationDialogData,
    private dialogRef: MatDialogRef<UploadAsFolderDialogComponent>
  ) {
    this.form = new FormGroup({
      uploadAsFolder: new FormControl<boolean>(data.defaultUploadAsFolder ?? false, {nonNullable: true}),
      folderName: new FormControl<string>(data.defaultFolderName ?? '', {nonNullable: true}),
    });

    const applyFolderNameValidators = (uploadAsFolder: boolean) => {
      const folder = this.form.controls.folderName;
      if (uploadAsFolder) folder.setValidators([Validators.required, Validators.minLength(1)]);
      else folder.clearValidators();
      folder.updateValueAndValidity({emitEvent: false});
    };

    // apply once + on changes
    applyFolderNameValidators(this.form.controls.uploadAsFolder.value);
    this.form.controls.uploadAsFolder.valueChanges.subscribe(applyFolderNameValidators);
  }

  onConfirm() {
    if (this.form.invalid) return;

    const uploadAsFolder = this.form.controls.uploadAsFolder.value;
    const folderName = this.form.controls.folderName.value.trim();

    this.dialogRef.close({
      confirmed: true,
      uploadAsFolder,
      folderName: uploadAsFolder ? folderName : undefined,
    } satisfies ConfirmationDialogResult);
  }

  onCancel() {
    this.dialogRef.close({confirmed: false, uploadAsFolder: false} satisfies ConfirmationDialogResult);
  }
}
