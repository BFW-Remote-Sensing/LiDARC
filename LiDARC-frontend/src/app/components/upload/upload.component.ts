import { Component, EventEmitter, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatList, MatListItem } from '@angular/material/list';
import { FileInfo } from '../../dto/fileInfo';
import { MatIcon } from '@angular/material/icon';
import { MatTable, MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { BehaviorSubject, Observable } from 'rxjs';
import { DataSource } from '@angular/cdk/collections';
import { UploadService } from '../../service/upload.service';
import { HttpEventType, HttpResponse } from '@angular/common/http';

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
  ],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.scss'],
})
export class UploadComponent {
  public files: UploadFile[] = [];

  // observable subject holding the current files list
  private filesSubject = new BehaviorSubject<UploadFile[]>([]);
  public files$ = this.filesSubject.asObservable();
  // DataSource wrapper for the mat-table
  public dataSource = new FilesDataSource(this.files$);

  columnsToDisplay = ['file name', 'actions'];
  @Output() filesChange = new EventEmitter<UploadFile[]>();

  form: FormGroup;

  constructor(private fb: FormBuilder, private uploadService: UploadService) {
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
    for (const f of newFiles) {
      const exists = this.files.some((e) => e.file.name === f.name && e.file.type === f.type);
      if (!exists) this.files.push({ file: f, progress: 0, status: 'idle' });
    }
    // update reactive form control so parent or validators can observe
    this.filesSubject.next([...this.files]);
    this.form.get('files')!.setValue(this.files);
    this.filesChange.emit(this.files);
  }

  upload(fileUpload: UploadFile) {
    if (fileUpload.status === 'uploading') return;

    fileUpload.status = 'uploading';
    fileUpload.progress = 0;
    this.filesSubject.next([...this.files]);

    this.uploadService.uploadFileUsingPresign(fileUpload.file).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          fileUpload.progress = Math.round((100 * event.loaded) / event.total);
          this.filesSubject.next([...this.files]);
        } else if (event instanceof HttpResponse) {
          fileUpload.progress = 100;
          fileUpload.status = 'done';
          this.filesSubject.next([...this.files]);
        }
      },
      error: () => {
        fileUpload.status = 'error';
        this.filesSubject.next([...this.files]);
      },
    });
  }

  removeFile(fileUpload: UploadFile) {
    // Filter by file identity using name, size and type to avoid relying on object reference
    this.files = this.files.filter(
      (f) => !(f.file.name === fileUpload.file.name && f.file.type === fileUpload.file.type)
    );
    this.filesSubject.next([...this.files]);
    this.form.get('files')!.setValue(this.files);
    this.filesChange.emit(this.files);
  }

  clear() {
    this.files = [];
    this.filesSubject.next([...this.files]);
    this.form.get('files')!.setValue(this.files);
    this.filesChange.emit(this.files);
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
