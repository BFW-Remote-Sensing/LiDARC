import { Component, inject, Input, signal, WritableSignal } from '@angular/core';
import { ComparisonService } from '../../service/comparison.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormatService } from '../../service/format.service';
import { MetadataService } from '../../service/metadata.service';
import { finalize, forkJoin } from 'rxjs';
import { ComparisonDTO } from '../../dto/comparison';
import { ComparisonReport } from '../../dto/comparisonReport';
import { CommonModule } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIcon } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { TextCard } from '../text-card/text-card';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  selector: 'app-comparison-details',
  imports: [
    CommonModule,
    RouterModule,
    MatIcon,
    MatIconButton,
    MatTooltip,
    MatExpansionModule,
    MatListModule,
    TextCard,
    MatProgressSpinner
  ],
  templateUrl: './comparison-details.html',
  styleUrls: ['./comparison-details.scss', '../file-details/file-details.scss'],
})
export class ComparisonDetails {
  @Input() comparisonId: number | null = null;
  @Input() comparison: ComparisonDTO | null = null;
  @Input() reports: ComparisonReport[] = [];
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);

  constructor(
    private comparisonService: ComparisonService,
    private route: ActivatedRoute,
  ) {
    this.comparisonId = Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    if (this.comparisonId) {
      this.loading.set(true);
      forkJoin({
        comparison: this.comparisonService.getComparisonById(+this.comparisonId),
        reports: this.comparisonService.getComparisonReportsById(+this.comparisonId)
      })
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: ({ comparison, reports }) => {
            this.comparison = comparison;
            this.reports = reports;
          },
          error: (error) => {
            console.error('Error fetching comparison data:', error);
            this.errorMessage.set('Failed to fetch comparison data. Please try again later.');
          }
        });
    }

  }
}
