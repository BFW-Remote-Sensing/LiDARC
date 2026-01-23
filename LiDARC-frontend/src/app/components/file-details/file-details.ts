import { Component, inject, Input, signal, WritableSignal, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { finalize, interval, Subject, switchMap, takeUntil } from 'rxjs';
import { FormatService } from '../../service/format.service';
import { MetadataService } from '../../service/metadata.service';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ActivatedRoute } from '@angular/router';
import { TextCard } from '../text-card/text-card';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { pollingIntervalMs, snackBarDurationMs } from '../../globals/globals'; // Import the global interval
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { StatusService } from '../../service/status.service';

@Component({
  selector: 'app-file-details',
  standalone: true, // Ensure it's standalone if used as such
  imports: [
    CommonModule,
    MatIconModule,
    MatExpansionModule,
    MatListModule,
    TextCard,
    MatProgressSpinner,
    FormatBytesPipe,
    MatTooltipModule,
  ],
  templateUrl: './file-details.html',
  styleUrls: ['./file-details.scss', '../stored-files/stored-files.scss'],
})
export class FileDetails implements OnInit, OnDestroy {
  @Input() metadataId: number | null = null;
  private metadataService = inject(MetadataService);
  public metadata = signal<FileMetadataDTO | null>(null);
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  private stopPolling$ = new Subject<void>();
  private isPolling = false;
  private previousFileStatus: string | null = null;

  constructor(private route: ActivatedRoute, private formatService: FormatService, private statusService: StatusService, private snackBar: MatSnackBar) {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.metadataId = idParam ? Number(idParam) : null;
  }

  ngOnInit(): void {
    this.fetchMetadata();
  }

  private fetchMetadata(): void {
    if (!this.metadataId) return;

    this.loading.set(true);
    this.metadataService.getMetadataById(this.metadataId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => this.handleMetadataUpdate(data),
        error: (error) => {
          console.error('Error fetching metadata:', error);
          this.errorMessage.set('Failed to fetch metadata.');
        }
      });
  }

  private handleMetadataUpdate(data: FileMetadataDTO): void {
    const formattedData = this.formatService.formatMetadata(data); //

    if (this.previousFileStatus && this.previousFileStatus !== formattedData.status) {
      this.snackBar.open(
        this.statusService.getComparableSnackbarMessage(
          "File",
          formattedData.originalFilename,
          formattedData.status
        ),
        'OK',
        { duration: snackBarDurationMs } //
      );
    }

    this.previousFileStatus = formattedData.status;
    this.metadata.set(formattedData);

    this.checkAndStartPolling();
  }

  private checkAndStartPolling(): void {
    if (!this.metadata()) return;

    const needsPolling = this.metadata()!.status !== 'PROCESSED' && this.metadata()!.status !== 'FAILED';

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
        switchMap(() => this.metadataService.getMetadataById(this.metadataId!))
      )
      .subscribe({
        next: (data) => {
          this.metadata.set(this.formatService.formatMetadata(data));
          if (this.metadata()!.status === 'PROCESSED' || this.metadata()!.status === 'FAILED') {
            this.stopPolling$.next();
            this.isPolling = false;
          }
        },
        error: (error) => {
          console.error('Polling error:', error);
          this.isPolling = false;
          this.stopPolling$.next();
        }
      });
  }

  ngOnDestroy(): void {
    this.stopPolling$.next();
    this.stopPolling$.complete();
  }
}