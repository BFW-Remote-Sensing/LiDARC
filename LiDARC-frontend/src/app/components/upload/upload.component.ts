import { ChangeDetectorRef, Component, EventEmitter, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTable, MatTableModule } from '@angular/material/table';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { DataSource } from '@angular/cdk/collections';
import { UploadService } from '../../service/upload.service';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { debounceTime, map, Observable } from 'rxjs';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

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
  ],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.scss'],
})
export class UploadComponent {
  public files: UploadFile[] = [];
  columnsToDisplay = ['file name', 'status', 'actions'];
  public fileToSubscriptionMap: Map<UploadFile, any> = new Map();
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private uploadService: UploadService,
    private cdr: ChangeDetectorRef,
    private toastr: ToastrService
  ) {
    this.form = this.fb.group({
      // store selected files array in the control; adapt type as needed
      files: [[]],
    });
  }

  onFileInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.addFiles(Array.from(input.files));
    input.value = '';
  }

  addFiles(newFiles: File[]) {
    let changed = false;
    const changedFiles: UploadFile[] = [];

    for (const f of newFiles) {
      const exists = this.files.some((e) => e.file.name === f.name && e.file.type === f.type);
      if (!exists) {
        const actualFile: UploadFile = { file: f, hash: '', progress: 0, status: 'idle' };
        this.files.push(actualFile);
        changedFiles.push(actualFile);
        changed = true;
      }
    }

    if (changed) {
      this.files = [...this.files]; // one immutable update
      changedFiles.forEach((file) => this.fileToSubscriptionMap.set(file, this.upload(file)));
    }
  }

  upload(fileUpload: UploadFile) {
    if (fileUpload.status === 'uploading') return;
    fileUpload.status = 'uploading';
    this.cdr.detectChanges(); //need to detect Changes for some reason
    return this.uploadService
      .uploadFileUsingPresign(fileUpload.file, fileUpload)
      .pipe(debounceTime(50))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total != null) {
            const newProgress = Math.round((100 * event.loaded) / event.total);
            this.files = this.files.map((file) => {
              if (file === fileUpload) {
                file.status = 'uploading';
                this.cdr.detectChanges(); //need to detect Changes for some reason
                file.progress = newProgress;
                this.cdr.detectChanges(); //need to detect Changes for some reason
                return file;
              } else {
                return file;
              }
            });
            this.form.get('files')!.setValue(this.files);
          } else if (event instanceof HttpResponse) {
            if (event.status == 200) {
              this.files = this.files.map((file) => {
                if (file === fileUpload) {
                  file.status = 'done';
                  this.cdr.detectChanges(); //need to detect Changes for some reason
                  file.progress = 100;
                  this.cdr.detectChanges(); //need to detect Changes for some reason
                  this.fileToSubscriptionMap.delete(fileUpload);

                  this.uploadService.onComplete?.(fileUpload.file, fileUpload.hash).subscribe({
                    next: (res) => {
                      console.log(
                        'notified backend of completed upload for file ' + fileUpload.file.name
                      );
                    },
                    error: (e) => {
                      console.error(
                        'could not notify backend of completed upload for file ' +
                          fileUpload.file.name +
                          ' ' +
                          e.status
                      );
                      file.status = 'error';
                      this.cdr.detectChanges(); //need to detect Changes for some reason
                    },
                  });

                  return file;
                } else {
                  return file;
                }
              });
              this.form.get('files')!.setValue(this.files);
            } else {
              //TODO test this
              this.toastr.error(
                'Upload failed file with name: ' +
                  fileUpload.file.name +
                  ' \n Consider reuploading ' +
                  'StatusCode: ' +
                  event.status
              );
              this.files = this.files.map((file) => {
                if (file === fileUpload) {
                  file.status = 'error';
                  this.cdr.detectChanges(); //need to detect Changes for some reason
                  return file;
                } else {
                  return file;
                }
              });
            }
          }
        },
        error: (e) => {
          this.toastr.error(
            'Could not upload file with name: ' +
              fileUpload.file.name +
              ' \n Consider reuploading ' +
              e.status
          );
          fileUpload.status = 'error';
          this.cdr.detectChanges(); //need to detect Changes for some reason
          this.form.get('files')!.setValue(this.files);
        },
      });
  }
  reUpload(fileUpload: UploadFile) {
    this.fileToSubscriptionMap.set(fileUpload, this.upload(fileUpload));
  }

  cancel(uploadFile: UploadFile) {
    const subscription = this.fileToSubscriptionMap.get(uploadFile);
    if (subscription) {
      subscription.unsubscribe();
      this.fileToSubscriptionMap.delete(uploadFile);
      uploadFile.status = 'error'; //needs to be different than 'idle' to allow reupload
      uploadFile.progress = 0;
      this.files = [...this.files]; // one immutable update
      this.form.get('files')!.setValue(this.files);
    }
  }

  removeFile(fileUpload: UploadFile) {
    // Filter by file identity using name, size and type to avoid relying on object reference
    this.files = this.files.filter(
      (f) => !(f.file.name === fileUpload.file.name && f.file.type === fileUpload.file.type)
    );
    this.form.get('files')!.setValue(this.files);
  }

  clear() {
    this.files = [];
    this.form.get('files')!.setValue(this.files);
  }

  openFilePicker(input: HTMLInputElement) {
    input.click();
  }
}

// small DataSource implementation that connects to the files observable
class FilesDataSource extends DataSource<UploadFile> {
  constructor(private files$: Observable<UploadFile[]>) {
    super();
  }

  connect(): Observable<UploadFile[]> {
    return this.files$;
  }
  disconnect(): void {}
}
