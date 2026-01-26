import {Component, inject, Input, OnInit, signal, WritableSignal, ViewChild, computed} from '@angular/core';
import { ComparisonService } from '../../service/comparison.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormatService } from '../../service/format.service';
import { MetadataService } from '../../service/metadata.service';
import { debounceTime, finalize, forkJoin, interval, map, Subject, Subscription, switchMap, timer } from 'rxjs';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { ComparisonDTO } from '../../dto/comparison';
import { ComparisonReport } from '../../dto/comparisonReport';
import { CommonModule } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIcon } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { TextCard } from '../text-card/text-card';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatCheckbox } from '@angular/material/checkbox';


import * as echarts from 'echarts/core';
import { EChartsCoreOption, ECElementEvent } from 'echarts/core';
import { NgxEchartsDirective, provideEchartsCore } from "ngx-echarts";
import { BarChart, BoxplotChart, HeatmapChart, LineChart, ScatterChart } from 'echarts/charts';
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { LegacyGridContainLabel } from 'echarts/features';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatDividerModule } from '@angular/material/divider';
import { MatCardModule } from '@angular/material/card';
import { Heatmap } from '../heatmap/heatmap';
import { ReportCreationDialogComponent } from '../report-creation-dialog-component/report-creation-dialog-component';
import { MatDialog } from '@angular/material/dialog';
import { ChartData } from '../../dto/report';
import { ECharts } from 'echarts';
import { Globals, pollingIntervalMs, snackBarDurationMs } from '../../globals/globals';

import {
  ChunkingResult,
  ChunkedCell,
  VegetationStats,
  CellEntry
} from '../../dto/chunking';
import { filter, takeUntil, takeWhile } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { ChunkingSettings } from '../chunking-settings/chunking-settings';
import { FormsModule } from '@angular/forms';
import { FolderDTO } from '../../dto/folder';
import { StatusService } from '../../service/status.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';
import { ReportSerivce } from '../../service/report.serivce';

//====HARDCODED VIS===//
echarts.use([
  HeatmapChart,
  ScatterChart,
  BoxplotChart,
  LineChart,
  BarChart,
  TooltipComponent,
  VisualMapComponent,
  GridComponent,
  CanvasRenderer,
  LegacyGridContainLabel,
  TitleComponent,
  LegendComponent,
  GraphicComponent,
  MarkLineComponent
]);

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
    MatProgressSpinner,
    NgxEchartsDirective,
    MatCardModule,
    MatDividerModule,
    MatGridListModule,
    Heatmap,
    MatButton,
    ChunkingSettings,
    FormsModule,
    MatCheckbox
  ],
  templateUrl: './comparison-details.html',
  styleUrls: ['./comparison-details.scss', '../file-details/file-details.scss', '../stored-files/stored-files.scss'],
  providers: [provideEchartsCore({ echarts })]
})
export class ComparisonDetails implements OnInit {
  @Input() comparisonId: number | null = null;
  public comparison = signal<ComparisonDTO | null>(null);
  @Input() reports = signal<ComparisonReport[]>([]);
  @ViewChild(Heatmap) heatmapComponent!: Heatmap;

  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);
  private chartInstances: { [key: string]: ECharts } = {};
  private scatterInstance!: ECharts;
  reportsLimit: number = 3;
  chunkingSize: number = 5;
  reportsLoading = signal<boolean>(false);
  hasMoreReports = signal<boolean>(true);
  showOutlierDots = signal<boolean>(true);

  private stopPolling$ = new Subject<void>();
  private isPolling = false;
  private previousStatus: string | null = null;

  constructor(
    private comparisonService: ComparisonService,
    private reportService: ReportSerivce,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    public globals: Globals,
    private statusService: StatusService,
    private snackBar: MatSnackBar,
    private router: Router
  ) {
    this.comparisonId = Number(this.route.snapshot.paramMap.get('id'));
  }

  chunkingResult!: ChunkingResult;
  vegetationStats = signal<VegetationStats>({
    cells: [],
    fileA_metrics: {
      min_veg_height: 0,
      max_veg_height: 0,
      mean_veg_height: 0,
      std_veg_height: 0,
      median_veg_height: 0,
      percentiles: { p10: 0, p25: 0, p50: 0, p75: 0, p90: 0 },
      mean_points_per_grid_cell: 0
    },
    fileB_metrics: {
      min_veg_height: 0,
      max_veg_height: 0,
      mean_veg_height: 0,
      std_veg_height: 0,
      median_veg_height: 0,
      percentiles: { p10: 0, p25: 0, p50: 0, p75: 0, p90: 0 },
      mean_points_per_grid_cell: 0
    },
    difference_metrics: {
      mean: 0,
      median: 0,
      std: 0,
      most_negative: 0,
      least_negative: 0,
      smallest_positive: 0,
      largest_positive: 0,
      correlation: {
        pearson_correlation: 0,
        regression_line: {
          slope: 0,
          intercept: 0,
          x_max: 0,
          x_min: 0,
        }
      },
      histogram: {
        bin_edges: [],
        counts: [],
      }
    },
    group_mapping: {
      a: "unknown",
      b: "unknown"
    }

  });

  groupMapping = computed(() => this.vegetationStats().group_mapping);
  private pollingSubscription?: Subscription;
  private chunkSize$ = new Subject<number>();



  scatterOption!: EChartsCoreOption;
  fileDistributionOption!: EChartsCoreOption;
  diffDistributionOption!: EChartsCoreOption;
  diffHistogramOption !: EChartsCoreOption;
  categoryBarChart !: EChartsCoreOption;
  boxplotOption!: EChartsCoreOption;

  sharedChunkingResult?: ChunkingResult;

  onChartInit(instance: any, chartName: string): void {
    this.chartInstances[chartName] = instance
  }
  onChunkingSliderChange(data: ChunkingResult) {
    console.log("Chunking data change detected in comparison-details: ", data);
    this.sharedChunkingResult = data;
    this.handleChunkingResult(data);
  }

  get comparedItems(): { id: number; name: string; type: 'file' | 'folder' }[] {
    const files = this.comparison()?.files.map(f => ({ id: f.id, name: f.originalFilename, type: 'file' as const })) ?? [];
    const folders = [this.comparison()?.folderA, this.comparison()?.folderB]
      .filter(f => f != null)
      .map(f => ({ id: f.id, name: f.name, type: 'folder' as const }));
    return [...files, ...folders];
  }

  ngOnInit(): void {
    if (this.comparisonId) {
      this.fetchInitialComparison();
    }
  }

  private fetchInitialComparison(): void {
    this.loading.set(true);
    this.comparisonService.getComparisonById(+this.comparisonId!)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => this.handleComparisonUpdate(data),
        error: (err) => {
          console.error(err);
          this.errorMessage.set('Failed to fetch comparison data.');
        }
      });
  }

  private handleComparisonUpdate(data: any): void {
    // Check for status changes to trigger snackbars if desired
    if (this.previousStatus && this.previousStatus !== data.status) {
      this.snackBar.open(
        this.statusService.getComparisonSnackbarMessage("Comparison", data.name, data.status),
        'OK', { duration: snackBarDurationMs }
      );
    }
    this.previousStatus = data.status;

    // Update the signal
    this.comparison.set(data);

    if (data.status === 'COMPLETED') {
      this.chunkingSize = this.computeInitialChunkingSize();
      this.stopPolling();
      this.fetchReports(); // Sequential call: only fetch reports when COMPLETED
    } else if (data.status === 'FAILED') {
      this.stopPolling();
    } else {
      this.startPolling();
    }
  }

  private startPolling(): void {
    if (this.isPolling) return;
    this.isPolling = true;

    interval(pollingIntervalMs)
      .pipe(
        takeUntil(this.stopPolling$),
        switchMap(() => this.comparisonService.getComparisonById(+this.comparisonId!))
      )
      .subscribe({
        next: (data) => this.handleComparisonUpdate(data),
        error: (err) => {
          console.error('Polling error:', err);
          this.stopPolling();
        }
      });
  }

  private fetchReports(): void {
    this.comparisonService.getComparisonReportsById(+this.comparisonId!, this.reportsLimit)
      .subscribe({
        next: (reports) => {
            this.reports.set(reports);
            this.checkIfMoreReportsExist(reports.length);
          },
          error: (err) => console.error('Failed to fetch reports:', err)
      });
  }

  private stopPolling(): void {
    this.stopPolling$.next();
    this.isPolling = false;
  }

  onDeleteComparison(): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to delete this comparison?',
      objectName: this.comparison() ? this.comparison()!.name : '',
      extensionMessage: this.reports().length > 0 ? `This comparison has ${this.reports().length} associated report(s) that will also be deleted.` : undefined,
      primaryButtonText: 'Delete',
      primaryButtonColor: 'warn',
      secondaryButtonText: 'Cancel',
      onConfirm: () => this.comparisonService.deleteComparisonById(this.comparisonId!),
      successActionText: 'Comparison deletion'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data,
      disableClose: true,
      autoFocus: false
    });

    dialogRef.afterClosed().subscribe(success => {
      if (success) {
        this.router.navigate(['/comparisons']);
      }
    });
  }

  deleteReport(id: number, name: string): void {
    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to delete this report?',
      objectName: name,
      primaryButtonText: 'Delete',
      primaryButtonColor: 'warn',
      secondaryButtonText: 'Cancel',
      onConfirm: () => this.reportService.deleteReport(id),
      successActionText: 'Report deletion'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data,
      disableClose: true,
      autoFocus: false
    });

    dialogRef.afterClosed().subscribe(success => {
      if (success) {
        this.reports.set(this.reports().filter(report => report.id !== id));
      }
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.stopPolling$.complete();
  }

  computeInitialChunkingSize(): number {
    if (!this.comparison()?.grid) return 16;

    const cellWidth = this.comparison()!.grid?.cellWidth ?? 10;
    const cellHeight = this.comparison()!.grid?.cellHeight ?? 10;
    const xRange = (this.comparison()!.grid?.xMax ?? 1000) - (this.comparison()!.grid?.xMin ?? 0);
    const yRange = (this.comparison()!.grid?.yMax ?? 1000) - (this.comparison()!.grid?.yMin ?? 0);

    console.log(this.comparison()!.grid?.xMax);
    console.log(this.comparison()!.grid?.xMin);
    console.log("xRange: " + xRange + ", yRange: " + yRange);


    const cellsX = Math.ceil(xRange / cellWidth);
    const cellsY = Math.ceil(yRange / cellHeight);
    const totalCells = cellsX * cellsY;

    // Ziel: ~100 × 100 = 10 000
    const TARGET_CELLS = 10_000;

    // Grober Faktor (wie stark wir zusammenfassen müssten)
    const roughFactor = Math.sqrt(totalCells / TARGET_CELLS);

    console.log("Initial chunking size calculation:" + roughFactor + " factor for " + totalCells + " total cells.");

    // 🎯 Nur ca. 6 Rückgabewerte
    if (totalCells <= 0) return 5;
    if (roughFactor <= 1.2) return 1;
    if (roughFactor <= 1.8) return 2;
    if (roughFactor <= 2.5) return 4;
    if (roughFactor <= 3.5) return 6;
    if (roughFactor <= 5.0) return 8;
    return 12;
  }

  private handleChunkingResult(result: ChunkingResult): void {
    console.log('[FINAL CHUNKING RESULT]', result);
    if (!result?.chunked_cells) {
      console.warn('Chunking result incomplete, skipping chart update.', result);
      return;
    }

    const flattenedCells = this.flattenCells(result.chunked_cells);
    console.log('[FLATTENED CELLS COUNT]', flattenedCells.length);
    console.log('[SAMPLE CELLS]', flattenedCells.slice(0, 5));
    console.log('[BACKEND STATISTICS]', result.statistics);
    console.log('[DIFFERENCE METRICS FROM BACKEND]', result.statistics?.difference);


    this.vegetationStats.set({
      cells: flattenedCells,
      fileA_metrics: result.statistics.file_a,
      fileB_metrics: result.statistics.file_b,
      difference_metrics: result.statistics.difference,
      group_mapping: result.group_mapping,
    });
    this.buildAllCharts();
  }

  private buildAllCharts(): void {
    if (!this.vegetationStats().cells?.length) {
      console.warn('No cells available, charts not built.');
      return;
    }

    console.log('[BUILDING CHARTS]');

    this.scatterOption = { ...this.buildScatterChart() };
    this.fileDistributionOption = { ...this.buildDistributionChart() };
    this.diffDistributionOption = { ...this.buildDifferenceDistributionChart() };
    this.diffHistogramOption = { ...this.buildDifferenceHistogramChart() };
    this.boxplotOption = { ...this.buildBoxplotChart() };


  }

  private flattenCells(matrix: any[][]): CellEntry[] {
    if (!Array.isArray(matrix)) return [];

    const cells: CellEntry[] = [];

    for (const row of matrix) {
      if (!Array.isArray(row)) continue;
      for (const cell of row) {
        if (!cell) continue;
        cells.push({
          A: cell.veg_height_max_a ?? 0,
          B: cell.veg_height_max_b ?? 0,
          delta_z: cell.delta_z ?? 0
        });
      }
    }

    return cells;
  }

  private statisticsReady(stats: any): boolean {
    return !!(
      stats?.file_a &&
      stats?.file_b &&
      stats?.difference &&
      typeof stats.difference.pearsonCorrelation === 'number'
    );
  }


  loadMoreReports(): void {
    if (this.comparisonId == null) return;

    this.reportsLimit += 5;
    this.fetchReports();
  }

  createReport(): void {
    const chartImages: ChartData[] = [];
    if (this.heatmapComponent) {
      if (this.heatmapComponent.chartInstance1) {
        //TODO: Choose a better fiting name && See if we can make them more interactive / important for the report? Currently flat image of vis.
        this.pushChartImage(chartImages, this.heatmapComponent.chartInstance1, 'Vegetation Heatmap Left', 'heatmap_left.png')
      }
      if (this.heatmapComponent.chartInstance2) {
        this.pushChartImage(chartImages, this.heatmapComponent.chartInstance2, 'Vegetation Heatmap Right', 'heatmap_right.png')
      }
    }
    const chartsToExport = [
      { key: 'scatter', name: 'Vegetation Height Scatter Plot', fileName: 'scatter_plot.png' },
      { key: 'boxplot', name: 'Vegetation Height Box Plot', fileName: 'box_plot.png' },
      { key: 'distribution', name: 'Vegetation Height Distribution', fileName: 'distribution.png' },
      { key: 'distribution_diff', name: 'Vegetation Height Distribution Differences', fileName: 'distribution_diff.png' },
      { key: 'histo_diff', name: 'Histogram Differences', fileName: 'histo_diff.png' }
    ];
    chartsToExport.forEach(chart => {
      const instance = this.chartInstances[chart.key];
      if (instance) {
        this.pushChartImage(chartImages, instance, chart.name, chart.fileName)
      } else {
        console.warn(`Instance not found for ${chart.name}. Is the chart visible in the DOM?`);
      }
    });

    const dialogRef = this.dialog.open(ReportCreationDialogComponent, {
      width: '600px',
      height: 'auto',
      data: {
        comparison: this.comparison(),
        availableCharts: chartImages
      }
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.fetchReports();
      }
    })
  }

  private pushChartImage(array: ChartData[], instance: any, name: string, fileName: string) {
    try {
      const dataUrl = instance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: '#fff',
        excludeComponents: ['toolbox']
      });
      array.push({
        name: name,
        fileName: fileName,
        blob: this.dataURItoBlob(dataUrl)
      });
    } catch (error) {
      //TODO: Add some error handling?
      console.error(`Failed to export image for ${name}`, error);
    }
  }

  private dataURItoBlob(dataURI: string): Blob {
    const byteString = atob(dataURI.split(',')[1]);
    const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];
    const ab = new ArrayBuffer(byteString.length);
    const ia = new Uint8Array(ab);
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i);
    }
    return new Blob([ab], { type: mimeString });
  }

  private checkIfMoreReportsExist(countLoaded: number) {
    if (countLoaded < this.reportsLimit) {
      this.hasMoreReports.set(false);
    } else {
      this.hasMoreReports.set(true);
    }
  }



  buildScatterChart(): EChartsCoreOption {
    const stats = this.vegetationStats();
    const scatterData = stats.cells.map(c => [c.A, c.B]);

    const regression = stats.difference_metrics.correlation?.regression_line;
    const pearson = stats.difference_metrics.correlation?.pearson_correlation;

    const lineData =
      regression && typeof regression.slope === 'number'
        ? [
          [regression.x_min, regression.slope * regression.x_min + regression.intercept],
          [regression.x_max, regression.slope * regression.x_max + regression.intercept]
        ]
        : [];

    return {
      title: {
        show: true,
        text: 'Vegetation Height Correlation',
        subtext:
          typeof pearson === 'number'
            ? `Pearson r = ${pearson.toFixed(2)}`
            : 'Pearson r = n/a',
        top: 0,
        left: 'center'
      },
      tooltip: {
        trigger: 'item',
        confine: true,
        formatter: (p: any) =>
          p.seriesType === 'scatter'
            ? `A: ${p.data[0]}<br>B: ${p.data[1]}`
            : null
      },
      xAxis: {
        name: 'veg_height_A',
        type: 'value'
      },
      yAxis: {
        name: 'veg_height_B',
        type: 'value'
      },
      series: [
        {
          type: 'scatter',
          data: scatterData,
          symbolSize: 10,
          name: 'Cells'
        },
        {
          type: 'line',
          data: lineData,
          showSymbol: false,
          lineStyle: { type: 'dashed', width: 2, color: 'red' },
          name: 'Regression Line'
        }
      ]
    };
  }

  private buildDistributionChart(): EChartsCoreOption {
    const stats = this.vegetationStats();
    const fileA_metrics = stats.fileA_metrics;
    const fileB_metrics = stats.fileB_metrics;

    // Helper function: PDF of normal distribution
    const normalPDF = (x: number, mean: number, std: number) => {
      const s = Math.max(std, 1e-6);
      return (1 / (s * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / s, 2));
    }

    // Define the range of x (min to max of both files)
    const minX = Math.min(
      fileA_metrics.mean_veg_height - 4 * fileA_metrics.std_veg_height,
      fileB_metrics.mean_veg_height - 4 * fileB_metrics.std_veg_height
    );
    const maxX = Math.max(
      fileA_metrics.mean_veg_height + 4 * fileA_metrics.std_veg_height,
      fileB_metrics.mean_veg_height + 4 * fileB_metrics.std_veg_height
    );


    const steps = 200;
    const step = (maxX - minX) / steps;
    const xValues = Array.from({ length: steps + 1 }, (_, i) => minX + i * step);

    // Compute y-values for both distributions
    const fileA_Y = xValues.map(x => normalPDF(x, fileA_metrics.mean_veg_height, fileA_metrics.std_veg_height));
    const fileB_Y = xValues.map(x => normalPDF(x, fileB_metrics.mean_veg_height, fileB_metrics.std_veg_height));

    return {
      title: {
        text: 'File Height Distributions',
        left: 'center',
        top: 0,
      },
      tooltip: {
        trigger: 'axis',
        confine: true
      },
      legend: {
        show: true,
        top: 20,
        left: 'center',
      },
      xAxis: {
        type: 'value',
        name: 'Vegetation Height'
      },
      yAxis: {
        type: 'value',
        name: 'Density'
      },
      series: [
        {
          name: 'File A',
          type: 'line',
          smooth: true,
          data: xValues.map((x, i) => [x, fileA_Y[i]]),
          lineStyle: { width: 2, color: 'blue' },
          showSymbol: false
        },
        {
          name: 'File B',
          type: 'line',
          smooth: true,
          data: xValues.map((x, i) => [x, fileB_Y[i]]),
          lineStyle: { width: 2, color: 'red' },
          showSymbol: false
        }
      ]
    };
  }

  private buildDifferenceDistributionChart(): EChartsCoreOption {
    const diff = this.vegetationStats().difference_metrics;
    const mean = diff.mean;
    const std = Math.max(diff.std, 1e-6);

    const normalPDF = (x: number) => (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));

    const minX = mean - 4 * std;
    const maxX = mean + 4 * std;

    const steps = 200;
    const step = (maxX - minX) / steps;
    const xValues = Array.from({ length: steps + 1 }, (_, i) => minX + i * step);

    const yValues = xValues.map(x => normalPDF(x));

    const maxY = Math.max(...yValues) * 1.1;

    return {
      title: {
        text: 'Distribution of Differences (B − A)',
        left: 'center',
        top: 0,
      },
      tooltip: {
        trigger: 'axis',
        confine: true
      },
      legend: {
        top: 20,
        left: 'center',
      },
      xAxis: {
        type: 'value',
        name: 'Vegetation Height Difference'
      },
      yAxis: {
        type: 'value',
        name: 'Density'
      },
      series: [
        {
          name: 'Difference PDF',
          type: 'line',
          smooth: true,
          data: xValues.map((x, i) => [x, yValues[i]]),
          lineStyle: { width: 2, color: 'green' },
          showSymbol: false
        },
        {
          name: 'Mean',
          type: 'line',
          data: [[mean, 0], [mean, maxY]],
          lineStyle: { type: 'dashed', width: 2 },
          showSymbol: false,
          tooltip: { show: false }
        },
        {
          name: '+2σ',
          type: 'line',
          data: [[mean + 2 * std, 0], [mean + 2 * std, maxY]],
          lineStyle: { type: 'dashed' },
          showSymbol: false,
          tooltip: { show: false }
        },
        {
          name: '-2σ',
          type: 'line',
          data: [[mean - 2 * std, 0], [mean - 2 * std, maxY]],
          lineStyle: { type: 'dashed' },
          showSymbol: false,
          tooltip: { show: false }
        }
      ]
    };
  }

  onOutlierToggle(): void {
    this.showOutlierDots.update((value) => !value);
  }

  private buildDifferenceHistogramChart(): EChartsCoreOption {
    const histogram = this.vegetationStats().difference_metrics.histogram;

    if (!histogram) {
      console.warn('No hisogram data available')
      return {};
    }

    const binEdges = histogram.bin_edges;
    const counts = histogram.counts;

    const binLabels: string[] = [];
    for (let i = 0; i < binEdges.length - 1; i++) {
      binLabels.push(`${binEdges[i].toFixed(2)} – ${binEdges[i + 1].toFixed(2)}`);
    }

    return {
      title: {
        text: 'Histogram of Differences (B - A)',
        left: 'center',
        top: 0,
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
        formatter: (params: any) => {
          const p = params[0];
          return `${p.axisValue}<br>Count: ${p.data}`;
        }
      },
      xAxis: {
        type: 'category',
        name: 'Difference B - A',
        data: binLabels,
        axisLabel: { rotate: 45 }
      },
      yAxis: {
        type: 'value',
        name: 'Count'
      },
      series: [
        {
          name: 'Count',
          type: 'bar',
          data: counts,
          itemStyle: { color: 'steelblue' },
          barGap: 0,
          barCategoryGap: '0%'
        }
      ]
    };
  }

  private buildBoxplotChart(): EChartsCoreOption {
    const stats = this.vegetationStats();
    const fileA_metrics = stats.fileA_metrics;
    const fileB_metrics = stats.fileB_metrics;

    const data = [
      {
        value: [
          fileA_metrics.min_veg_height,
          fileA_metrics.percentiles.p25,
          fileA_metrics.median_veg_height,
          fileA_metrics.percentiles.p75,
          fileA_metrics.max_veg_height
        ],
        tooltipValue: fileA_metrics
      },
      {
        value: [
          fileB_metrics.min_veg_height,
          fileB_metrics.percentiles.p25,
          fileB_metrics.median_veg_height,
          fileB_metrics.percentiles.p75,
          fileB_metrics.max_veg_height
        ],
        tooltipValue: fileB_metrics
      }
    ];

    const categories = ['File A', 'File B'];

    return {
      title: {
        text: 'Boxplot of Vegetation Heights',
        left: 'center',
        top: 0
      },
      tooltip: {
        trigger: 'item',
        confine: true,
        formatter: (param: any) => {
          const metrics = param.data.tooltipValue;
          const categories = ['File A', 'File B'];
          return `${categories[param.dataIndex]}<br>
            Min: ${metrics.min_veg_height}<br>
            Q1: ${metrics.percentiles.p25}<br>
            Median: ${metrics.median_veg_height}<br>
            Q3: ${metrics.percentiles.p75}<br>
            Max: ${metrics.max_veg_height}`;
        }
      },
      xAxis: {
        type: 'category',
        data: categories
      },
      yAxis: {
        type: 'value',
        name: 'Vegetation Height'
      },
      series: [
        {
          name: 'Boxplot',
          type: 'boxplot',
          data: data,
          itemStyle: { color: 'lightblue' }
        }
      ]
    };
  }

}
