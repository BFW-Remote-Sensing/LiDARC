import { CommonModule } from '@angular/common';
import { Component, inject, signal, ViewChild, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms'; import { MatButtonModule } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox'; import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator'; import { MatSelectModule } from '@angular/material/select';
import { MatTableDataSource, MatTableModule } from '@angular/material/table'; import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterModule } from '@angular/router'; import { finalize } from 'rxjs'; import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MetadataService } from '../../service/metadata.service'; import { FileMetadataDTO } from '../../dto/fileMetadata';
import { SelectedFilesService } from '../../service/selectedFile.service';

@Component({
  selector: 'app-stored-files',
  standalone: true,
  imports: [
    FormsModule,
    MatTableModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule,
    MatPaginatorModule,
    MatCheckbox,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatProgressSpinner],
  templateUrl: './stored-files.html',
  styleUrl: './stored-files.scss',
})

export class StoredFiles {
  displayedColumns: string[] = ['select', 'filename', 'status', 'actions'];
  dataSource = new MatTableDataSource<FileMetadataDTO>([]);
  selectedFileIds: Set<number> = new Set();
  private readonly metadataService = inject(MetadataService);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(private selectedFilesService: SelectedFilesService, private router: Router) { }

  fetchMetadata(): void {
    this.metadataService.getAllMetadata()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data: FileMetadataDTO[]) => {
          this.dataSource.data = data;
        }, error: (error) => { console.error('Error fetching metadata:', error); this.errorMessage.set('Failed to fetch metadata. Please try again later.'); }
      });
  }

  ngOnInit(): void {
    this.fetchMetadata();
  }

  ngAfterViewInit() {
    setTimeout(() => {
      if (this.paginator) {
        this.dataSource.paginator = this.paginator;
      }
    });
    const storedSelectedFileIds = localStorage.getItem('selectedFileIds');
    if (storedSelectedFileIds) {
      this.selectedFileIds = new Set(JSON.parse(storedSelectedFileIds));
    }
  }

  toggleSelection(id: number, event: any) {
    if (event.checked) {
      this.selectedFileIds.add(id);
    } else {
      this.selectedFileIds.delete(id);
    }
    localStorage.setItem('selectedFileIds', JSON.stringify(Array.from(this.selectedFileIds)));
  }

  isSelected(id: number): boolean {
    return this.selectedFileIds.has(id);
  }

  deleteSelectedFiles() {
    alert('Delete functionality not yet implemented.');
    this.selectedFileIds.clear();
    localStorage.removeItem('selectedFileIds');
  }

  goToComparison() {
    if (this.selectedFileIds.size === 2) {
      this.selectedFilesService.selectedIds = Array.from(this.selectedFileIds);
      this.router.navigate(['/comparison-setup']);
    }
  }
}