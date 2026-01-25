import { Component, inject, Input, signal, WritableSignal } from '@angular/core';
import { FolderFilesDTO } from '../../dto/folderFiles';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormatService } from '../../service/format.service';
import { finalize, interval, Subject, switchMap, takeUntil } from 'rxjs';
import { FolderService } from '../../service/folderService.service';
import { CommonModule } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { TextCard } from '../text-card/text-card';
import { getExtremeValue } from '../../helpers/extremeValue';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { pollingIntervalMs, snackBarDurationMs } from '../../globals/globals';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StatusService } from '../../service/status.service';
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';
import { MatDialog } from '@angular/material/dialog';

@Component({
  selector: 'app-folder-details',
  imports: [
    CommonModule,
    MatIconModule,
    MatExpansionModule,
    MatListModule,
    TextCard,
    MatProgressSpinner,
    FormatBytesPipe,
    MatTableModule,
    RouterModule,
    CommonModule,
    MatTooltipModule,
    MatButtonModule
  ],
  templateUrl: './folder-details.html',
  styleUrls: ['./folder-details.scss', '../file-details/file-details.scss', '../stored-files/stored-files.scss'],
})
export class FolderDetails {
  @Input() folderId: number | null = null;
  private folderService = inject(FolderService);
  // 1. Change to a Signal for reactivity
  public folder = signal<FolderFilesDTO | null>(null);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);
  private stopPolling$ = new Subject<void>();
  private isPolling = false;

  private previousFolderStatus: string | null = null;

  displayedColumns: string[] = ['filename', 'status', 'captureYear', 'sizeBytes', 'uploadedAt', 'actions'];
  dataSource = new MatTableDataSource<FileMetadataDTO>([]);

  constructor(private route: ActivatedRoute, private formatService: FormatService, private snackBar: MatSnackBar, private statusService: StatusService,
    private dialog: MatDialog, private router: Router
  ) {
    this.folderId = Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.fetchFolderData();
  }

  private fetchFolderData(): void {
    if (!this.folderId) return;

    this.loading.set(true);
    this.folderService.getFolderById(this.folderId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => this.processFolderData(data),
        error: (error) => {
          console.error('Error fetching folder:', error);
          this.errorMessage.set('Failed to fetch folder details.');
        }
      });
  }

  private processFolderData(data: any): void {
    const formattedFolder = this.formatService.formatFolderFiles(data);
    if (this.previousFolderStatus && this.previousFolderStatus !== formattedFolder.status) {
      this.snackBar.open(
        this.statusService.getComparableSnackbarMessage("Folder", formattedFolder.folderName, formattedFolder.status),
        'OK',
        { duration: snackBarDurationMs }
      );
    }

    this.previousFolderStatus = formattedFolder.status;
    const currentFiles = this.dataSource.data;

    const updatedFiles = formattedFolder.files.map(newFile => {
      const existingFile = currentFiles.find(f => f.id === newFile.id);

      if (existingFile && JSON.stringify(existingFile) === JSON.stringify(newFile)) {
        return existingFile;
      }
      return newFile;
    });

    this.folder.set({ ...formattedFolder, files: updatedFiles });
    this.dataSource.data = updatedFiles;

    this.checkAndStartPolling();
  }

  private checkAndStartPolling(): void {
    if (!this.folder() || this.folder() === null) return;

    const needsPolling = !['PROCESSED', 'FAILED'].includes(this.folder()!.status);

    if (needsPolling && !this.isPolling) {
      this.startPolling();
    } else if (!needsPolling && this.isPolling) {
      this.stopPolling$.next();
      this.isPolling = false;
    }
  }

  private startPolling(): void {
    this.isPolling = true;
    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.folderService.getFolderById(this.folderId!))
      )
      .subscribe({
        next: (data) => this.processFolderData(data),
        error: (err) => {
          console.error('Polling error:', err);
          this.isPolling = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.stopPolling$.next();
    this.stopPolling$.complete();
  }

  // Update helper methods to read from the signal
  getFolderSizeBytes(): number {
    const folder = this.folder();
    if (!folder || folder.files.length === 0) return 0;
    const sizes = folder.files.map(f => f.sizeBytes).filter((s): s is number => s != null);
    return sizes.reduce((acc, s) => acc + s, 0);
  }

  getCaptureYear(): string | null {
    const folder = this.folder();
    if (!folder || folder.files.length === 0) return null;
    const years = folder.files.map(f => f.captureYear).filter((y): y is number => y != null);
    if (years.length === 0) return "-";
    const min = Math.min(...years);
    const max = Math.max(...years);
    return min === max ? `${min}` : `${min}-${max}`;
  }


  getMinMinX(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.minX, 'min');
  }

  getMinMinY(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.minY, 'min');
  }

  getMinMinZ(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.minZ, 'min');
  }

  getMaxMaxX(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.maxX, 'max');
  }

  getMaxMaxY(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.maxY, 'max');
  }

  getMaxMaxZ(): number | null {
    return getExtremeValue(this.folder()?.files, f => f.maxZ, 'max');
  }

  onDeleteFolder(): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to delete this folder?',
      objectName: this.folder() ? this.folder()!.folderName : '',
      primaryButtonText: 'Delete',
      primaryButtonColor: 'warn',
      secondaryButtonText: 'Cancel',
      onConfirm: () => this.folderService.deleteFolderById(this.folderId!),
      successActionText: 'Folder deletion'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data,
      disableClose: true,
      autoFocus: false
    });

    dialogRef.afterClosed().subscribe(success => {
      if (success) {
        this.router.navigate(['/comparable-items']);
      }
    });
  }
}
