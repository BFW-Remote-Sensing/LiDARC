import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTable, MatTableModule } from '@angular/material/table';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { debounceTime, map, Observable } from 'rxjs';
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
    MatProgressBar,
  ],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.scss'],
})
export class UploadComponent {
  public files: UploadFile[] = [];
  columnsToDisplay = ['file name', 'actions'];

  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private uploadService: UploadService,
    private cdr: ChangeDetectorRef
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

    for (const f of newFiles) {
      const exists = this.files.some((e) => e.file.name === f.name && e.file.type === f.type);
      if (!exists) {
        this.files.push({ file: f, progress: 0, status: 'idle' });
        this.cdr.detectChanges;
        changed = true;
      }
    }

    if (changed) {
      this.files = [...this.files]; // one immutable update
    }
  }

  upload(fileUpload: UploadFile) {
    if (fileUpload.status === 'uploading') return;

    this.uploadService
      .uploadFileUsingPresign(fileUpload.file)
      .pipe(
        debounceTime(50),
        map((event: any) => event.target.value)
      )
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
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
            this.files = this.files.map((file) => {
              if (file === fileUpload) {
                file.status = 'done';
                this.cdr.detectChanges(); //need to detect Changes for some reason
                file.progress = 100;
                this.cdr.detectChanges(); //need to detect Changes for some reason
                return file;
              } else {
                return file;
              }
            });
            this.form.get('files')!.setValue(this.files);
          }
        },
        error: () => {},
      });
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
