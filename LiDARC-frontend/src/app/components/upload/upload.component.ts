// file: LiDARC-frontend/src/app/components/upload/upload.component.ts
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {MatTable, MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatCardModule} from '@angular/material/card';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';
import {ToastrService} from 'ngx-toastr';
import {Subject, Subscription} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {HttpEventType, HttpResponse} from '@angular/common/http';

import {UploadService} from '../../service/upload.service';
import {UploadQueueService} from '../../service/uploadQueue.service';
import {UploadFile} from '../../entity/UploadFile';
import {
  ConfirmationDialogResult,
  UploadAsFolderDialogComponent
} from '../upload-as-folder-dialogue/upload-as-folder-dialogue';

// ... other imports


@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatTableModule,
    MatTable,
    MatIcon,
    MatProgressSpinner,
    MatDialogModule,
  ],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.scss'],
})
export class UploadComponent implements OnInit, OnDestroy {

  dataSource = new MatTableDataSource<UploadFile>([]);
  columnsToDisplay = ['file name', 'status', 'actions'];
  form: FormGroup;
  private subscription?: Subscription;
  private destroy$ = new Subject<void>();
  private notifiedFolders = new Set<number>();

  constructor(
    private fb: FormBuilder,
    private uploadService: UploadService,
    private uploadQueue: UploadQueueService,
    private toastr: ToastrService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({files: [[]]});
  }

  ngOnInit() {
    // Subscribe to queue and trigger change detection for each update
    this.subscription = this.uploadQueue.files$
      .pipe(takeUntil(this.destroy$))
      .subscribe(files => {
        this.dataSource.data = files;
        this.form.patchValue({files}, {emitEvent: false});
        this.checkFolderCompletion(files);
        this.cdr.detectChanges(); // ✓ Immediate change detection
      });

  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.subscription?.unsubscribe();
  }


  onFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.addFiles(Array.from(input.files));
    input.value = '';
  }

  addFiles(newFiles: File[]) {
    const filesToAdd = newFiles.filter(f =>
      !this.dataSource.data.some(e => e.file.name === f.name && e.file.type === f.type)
    );

    if (filesToAdd.length === 0) return;

    if (filesToAdd.length > 1) {
      this.showFolderDialog(filesToAdd);
    } else {
      const added = this.uploadQueue.addFiles(filesToAdd);
      added.forEach(f => this.startUpload(f));
    }
  }

  upload(fileUpload: UploadFile) {
    this.startUpload(fileUpload);
  }

  reUpload(fileUpload: UploadFile) {
    this.startUpload(fileUpload);
  }

  cancel(uploadFile: UploadFile) {
    this.uploadQueue.cancelUpload(uploadFile.id);
  }

  clear() {
    this.notifiedFolders.clear();
    this.uploadQueue.clear();
  }

  openFilePicker(input: HTMLInputElement) {
    input.click();
  }

  trackById = (_: number, row: UploadFile) => row.id;

  private showFolderDialog(files: File[]) {
    const ref = this.dialog.open(UploadAsFolderDialogComponent, {
      width: '420px',
      data: {
        title: 'Uploading multiple files',
        subtitle: 'Do you want to upload them as a folder?',
        primaryButtonText: 'Yes',
        secondaryButtonText: 'No',
        defaultUploadAsFolder: true,
        defaultFolderName: 'New folder',
        showAsFolderOption: true,
      },
      disableClose: true,
    });

    ref.afterClosed().subscribe((result?: ConfirmationDialogResult) => {
      if (!result?.confirmed) {
        const added = this.uploadQueue.addFiles(files);
        added.forEach(f => this.startUpload(f));
        this.cdr.detectChanges(); // ✓ Detect files added
        return;
      }

      if (result.uploadAsFolder) {
        this.uploadService.getEmptyFolder(result.folderName!).subscribe({
          next: (folder) => {
            const added = this.uploadQueue.addFiles(files, folder.id, folder.name);
            added.forEach(f => this.startUpload(f));
            this.cdr.detectChanges(); // ✓ Detect files added to folder
          },
          error: () => {
            this.toastr.error('Could not create folder. Uploading without folder.');
            const added = this.uploadQueue.addFiles(files);
            added.forEach(f => this.startUpload(f));
            this.cdr.detectChanges(); // ✓ Detect files added after error
          }
        });
      }
    });
  }

  private startUpload(fileUpload: UploadFile) {
    if (fileUpload.status === 'uploading') return;

    this.uploadQueue.updateFile(fileUpload.id, {status: 'uploading', progress: 0});
    this.cdr.detectChanges(); // ✓ Detect upload start

    const sub = this.uploadService
      .uploadFileUsingPresign(fileUpload.file, fileUpload)
      .subscribe({
        next: event => {
          this.handleUploadEvent(event, fileUpload);
          this.cdr.detectChanges(); // ✓ Detect progress
        },
        error: (e) => {
          console.error('Upload pipeline error for file ' + fileUpload.file.name, e);
          this.handleUploadError(e, fileUpload);
          this.cdr.detectChanges(); // ✓ Detect error
        }
      });

    this.uploadQueue.setSubscription(fileUpload.id, sub);
  }

  private handleUploadEvent(event: any, fileUpload: UploadFile) {
    if (event.type === HttpEventType.UploadProgress && event.total) {
      const progress = Math.round((100 * event.loaded) / event.total);
      this.uploadQueue.updateFile(fileUpload.id, {progress});
    } else if (event instanceof HttpResponse) {
      if (event.status === 200) {
        this.uploadQueue.updateFile(fileUpload.id, {status: 'done', progress: 100});
        this.cdr.detectChanges(); // ✓ Detect completion

        this.uploadService.onComplete?.(fileUpload.file, fileUpload.hash).subscribe({
          next: () => {
            console.log('Upload completed:', fileUpload.file.name);
            this.cdr.detectChanges(); // ✓ Detect completion callback
          },
          error: (err) => {
            const statusCode = err?.status || 'Unknown';
            const errorMsg = err?.error?.message || err?.message || 'Failed to notify backend';
            console.error('Failed to notify backend:', err);
            this.toastr.error(`${errorMsg} (Status: ${statusCode})`, `Backend Notification Failed: ${fileUpload.file.name}`);
            this.uploadQueue.updateFile(fileUpload.id, {status: 'error'});
            this.cdr.detectChanges(); // ✓ Detect backend error
          }
        });
      } else {
        const statusText = event.statusText || 'Unknown Error';
        this.toastr.error(
          `${fileUpload.file.name} - ${statusText} (Status: ${event.status})`,
          'Upload Failed'
        );
        this.uploadQueue.updateFile(fileUpload.id, {status: 'error'});
        this.cdr.detectChanges(); // ✓ Detect error status
      }
    }
  }

  private handleUploadError(_error: any, fileUpload: UploadFile) {
    console.error('handleUploadError called for file:', fileUpload.file.name, 'error:', _error);

    // Extract status code and error details
    const statusCode = _error?.status || _error?.statusCode || 'Unknown';
    const statusText = _error?.statusText || '';
    const errorMessage = _error?.error?.message || _error?.message || 'Upload failed';

    // Build descriptive error message
    let errorDetail = errorMessage;
    if (statusText && statusText !== errorMessage) {
      errorDetail += ` - ${statusText}`;
    }

    this.toastr.error(
      `${fileUpload.file.name}: ${errorDetail} (Status: ${statusCode})`,
      'Upload Error'
    );
    this.uploadQueue.updateFile(fileUpload.id, {status: 'error', progress: 0});
    this.cdr.detectChanges(); // ✓ Detect error immediately
  }


  private checkFolderCompletion(files: UploadFile[]) {
    // group by folderId (ignore uploads not in a folder)
    const byFolder = new Map<number, UploadFile[]>();

    for (const f of files) {
      if (f.folderId == null) continue;
      const list = byFolder.get(f.folderId) ?? [];
      list.push(f);
      byFolder.set(f.folderId, list);
    }

    for (const [folderId, folderFiles] of byFolder.entries()) {
      if (folderFiles.length === 0) continue;
      if (this.notifiedFolders.has(folderId)) continue;

      // if count allfiles + any error = folderFiles.length, then all are done (either folder is uploaded)
      const allFinished = folderFiles.every(x => x.status === 'done' || x.status === 'error');

      if (allFinished && !this.notifiedFolders.has(folderId)) {
        this.notifiedFolders.add(folderId);

        // call backend endpoint to set folder status
        this.uploadService.markFolderComplete(folderId).subscribe({
          next: () => {
            // optional toast/log
          },
          error: () => {
            // allow retry if backend call fails
            this.notifiedFolders.delete(folderId);
            this.toastr.error(`Could not update folder status (${folderId}).`);
          }
        });
      }

      // Optional alternative:
      // if (allDone && anyError) { mark folder as ERROR instead }
    }
  }
}
