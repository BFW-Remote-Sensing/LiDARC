import {Component, inject, Input, OnInit, signal, WritableSignal} from '@angular/core';
import {ComparisonService} from '../../service/comparison.service';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {FormatService} from '../../service/format.service';
import {MetadataService} from '../../service/metadata.service';
import {debounceTime, finalize, forkJoin, map, Subject, Subscription, switchMap, timer} from 'rxjs';
import {FormatBytesPipe} from '../../pipes/formatBytesPipe';
import {ComparisonDTO} from '../../dto/comparison';
import {ComparisonReport} from '../../dto/comparisonReport';
import {CommonModule} from '@angular/common';
import {MatExpansionModule} from '@angular/material/expansion';
import {MatIcon} from '@angular/material/icon';
import {MatListModule} from '@angular/material/list';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {TextCard} from '../text-card/text-card';
import {MatButton, MatIconButton} from '@angular/material/button';
import {MatTooltip} from '@angular/material/tooltip';


import * as echarts from 'echarts/core';
import {EChartsCoreOption} from 'echarts/core';
import {NgxEchartsDirective, provideEchartsCore} from "ngx-echarts";
import {BarChart, BoxplotChart, HeatmapChart, LineChart, ScatterChart} from 'echarts/charts';
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {LegacyGridContainLabel} from 'echarts/features';
import {MatGridListModule} from '@angular/material/grid-list';
import {MatDividerModule} from '@angular/material/divider';
import {MatCardModule} from '@angular/material/card';
import {Heatmap} from '../heatmap/heatmap';
import {ReportCreationDialogComponent} from '../report-creation-dialog-component/report-creation-dialog-component';
import {MatDialog} from '@angular/material/dialog';
import {ChartData} from '../../dto/report';
import {ECharts} from 'echarts';
import {Globals} from '../../globals/globals';

import {
  ChunkingResult,
  ChunkedCell,
  VegetationStats,
  CellEntry
} from '../../dto/chunking';
import {filter, takeWhile} from 'rxjs/operators';
import {HttpResponse} from '@angular/common/http';
import {ChunkingSettings} from '../chunking-settings/chunking-settings';
import {FormsModule} from '@angular/forms';

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
    FormsModule
  ],
  templateUrl: './comparison-details.html',
  styleUrls: ['./comparison-details.scss', '../file-details/file-details.scss'],
  providers: [provideEchartsCore({echarts})]
})
export class ComparisonDetails implements OnInit {
  @Input() comparisonId: number | null = null;
  @Input() comparison: ComparisonDTO | null = null;
  @Input() reports: ComparisonReport[] = [];
  public loading: WritableSignal<boolean> = signal(true);
  public errorMessage = signal<string | null>(null);
  private scatterInstance!: ECharts;
  reportsLimit: number = 4;
  reportsLoading = signal(false);
  hasMoreReports = signal(true);

  constructor(
    private comparisonService: ComparisonService,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    public globals: Globals
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
      percentiles: {p10: 0, p25: 0, p50: 0, p75: 0, p90: 0}
    },
    fileB_metrics: {
      min_veg_height: 0,
      max_veg_height: 0,
      mean_veg_height: 0,
      std_veg_height: 0,
      median_veg_height: 0,
      percentiles: {p10: 0, p25: 0, p50: 0, p75: 0, p90: 0}
    },
    difference_metrics: {
      mean: 0,
      median: 0,
      std: 0,
      most_negative: 0,
      least_negative: 0,
      smallest_positive: 0,
      largest_positive: 0,
      pearson_corr: 0,
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
  private pollingSubscription?: Subscription;
  private chunkSize$ = new Subject<number>();
  chunkSize = 16;


  scatterOption!: EChartsCoreOption;
  fileDistributionOption!: EChartsCoreOption;
  diffDistributionOption!: EChartsCoreOption;
  diffHistogramOption !: EChartsCoreOption;
  categoryBarChart !: EChartsCoreOption;
  boxplotOption!: EChartsCoreOption;

  sharedChunkingResult?: ChunkingResult;

  onChunkingSliderChange(data: ChunkingResult) {
    console.log("Chunking data change detected in comparison-details: ", data);
    this.sharedChunkingResult = data;
    this.handleChunkingResult(data);
  }


  ngOnInit(): void {
    if (this.comparisonId) {
      this.loading.set(true);
      forkJoin({
        comparison: this.comparisonService.getComparisonById(+this.comparisonId),
        reports: this.comparisonService.getComparisonReportsById(+this.comparisonId, this.reportsLimit)
      })
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: ({comparison, reports}) => {
            this.comparison = comparison;
            this.reports = reports;
            this.checkIfMoreReportsExist(reports.length);

          },
          error: (err) => {
            console.error(err);
            this.errorMessage.set('Failed to fetch comparison data.');
          }
        });
    }
  }

  private handleChunkingResult(result: ChunkingResult): void {
    console.log('[FINAL CHUNKING RESULT]', result);
    if (!result?.chunked_cells) {
      console.warn('Chunking result incomplete, skipping chart update.', result);
      return;
    }

    this.vegetationStats.set({
      cells: this.flattenCells(result.chunked_cells),
      fileA_metrics: result.statistics.file_a,
      fileB_metrics: result.statistics.file_b,
      difference_metrics: result.statistics.difference,
      group_mapping: result.group_mapping,
    });
    console.log('[FLATTENED CELLS COUNT]', this.vegetationStats().cells.length);

    this.buildAllCharts();
  }

  private buildAllCharts(): void {
    if (!this.vegetationStats().cells?.length) {
      console.warn('No cells available, charts not built.');
      return;
    }

    console.log('[BUILDING CHARTS]');

    this.scatterOption = {...this.buildScatterChart()};
    this.fileDistributionOption = {...this.buildDistributionChart()};
    this.diffDistributionOption = {...this.buildDifferenceDistributionChart()};
    this.diffHistogramOption = {...this.buildDifferenceHistogramChart()};
    this.boxplotOption = {...this.buildBoxplotChart()};


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

  createReport():void {
    const chartImages:ChartData[] = [];
    console.log('Scatter Instance:', this.scatterInstance);
    if (this.scatterInstance) {
      const dataUrl = this.scatterInstance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: '#fff',
        excludeComponents: ['toolbox']
      });
      chartImages.push({
        name: 'Vegetation Scatter Plot',
        fileName: 'scatter_plot.png',
        blob: this.dataURItoBlob(dataUrl)
      });
    }

    const dialogRef = this.dialog.open(ReportCreationDialogComponent, {
      width: '600px',
      height: 'auto',
      data: {
        comparison: this.comparison,
        availableCharts: chartImages
      }
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.fetchReports();
      }
    })
  }

  dataURItoBlob(dataURI
                :
                string
  ):
    Blob {
    const byteString = atob(dataURI.split(',')[1]);
    const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0];
    const ab = new ArrayBuffer(byteString.length);
    const ia = new Uint8Array(ab);
    for (let i = 0; i < byteString.length; i++) {
      ia[i] = byteString.charCodeAt(i);
    }
    return new Blob([ab], {type: mimeString});
  }

  private fetchReports(): void {
    if (!this.comparisonId) return;

    // Use the signal for loading state
    this.reportsLoading.set(true);

    this.comparisonService.getComparisonReportsById(+this.comparisonId, this.reportsLimit)
      .pipe(finalize(() => this.reportsLoading.set(false)))
      .subscribe({
        next: (reports: ComparisonReport[]) => {
          this.reports = reports;
          this.checkIfMoreReportsExist(reports.length);
        },
        error: () => {
          // Handle error silently or show toast
          console.error('Could not refresh reports list');
        }
      });
  }

  private checkIfMoreReportsExist(countLoaded: number) {
    if (countLoaded < this.reportsLimit) {
      this.hasMoreReports.set(false);
    } else {
      this.hasMoreReports.set(true);
    }
  }



  buildScatterChart():EChartsCoreOption {
    const scatterData = this.vegetationStats().cells.map(c => [c.A, c.B]);

    // Linear regression TODO should be done in worker
    const n = scatterData.length;
    const sumX = scatterData.reduce((s, [x]) => s + x, 0);
    const sumY = scatterData.reduce((s, [, y]) => s + y, 0);
    const sumXY = scatterData.reduce((s, [x, y]) => s + x * y, 0);
    const sumX2 = scatterData.reduce((s, [x]) => s + x * x, 0);
    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;


    let minX = Infinity, maxX = -Infinity;
    let minY = Infinity, maxY = -Infinity;

    for (const [x, y] of scatterData) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    }


    const lineData = [
      [minX, slope * minX + intercept],
      [maxX, slope * maxX + intercept]
    ];
    const pearson = this.vegetationStats().difference_metrics.pearson_corr;
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
          lineStyle: {type: 'dashed', width: 2, color: 'red'},
          name: 'Regression Line'
        }
      ]
    };
  }

  private buildDistributionChart():EChartsCoreOption {
    const stats = this.vegetationStats();
    const fileA_metrics = stats.fileA_metrics;
    const fileB_metrics = stats.fileB_metrics;

    // Helper function: PDF of normal distribution
    const normalPDF = (x: number, mean: number, std: number) =>
      (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));

    // Define the range of x (min to max of both files)
    const minX = Math.floor(Math.min(fileA_metrics.min_veg_height, fileB_metrics.min_veg_height) - 5);
    const maxX = Math.ceil(Math.max(fileA_metrics.max_veg_height, fileB_metrics.max_veg_height) + 10);


    const step = (maxX - minX) / 100;
    const xValues = [];
    for (let x = minX; x <= maxX; x += step) xValues.push(x);

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
    const cells = this.vegetationStats().cells;

// --- 1) Mean & Std in einem stabilen Durchlauf (Welford) ---
    let n = 0;
    let mean = 0;
    let m2 = 0;

    let minDiff = Infinity;
    let maxDiff = -Infinity;

    for (const c of cells) {
      const d = c.B - c.A;
      n++;

      // Welford online algorithm
      const delta = d - mean;
      mean += delta / n;
      const delta2 = d - mean;
      m2 += delta * delta2;

      if (d < minDiff) minDiff = d;
      if (d > maxDiff) maxDiff = d;
    }

    const variance = n > 0 ? m2 / n : 0;
    const std = variance > 0 ? Math.sqrt(variance) : 0;

// --- 2) PDF (robust gegen std=0) ---
    const normalPDF = (x: number) => {
      if (std <= 0) return 0;
      const z = (x - mean) / std;
      return (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * z * z);
    };

// --- 3) X-Range ohne Spread ---
    const minX = Math.floor(minDiff - 1);
    const maxX = Math.ceil(maxDiff + 17);

// --- 4) Feste Sample-Anzahl (kein explodierendes Array) ---
    const SAMPLES = 101;
    const span = maxX - minX;
    const step = span > 0 ? span / (SAMPLES - 1) : 1;

    const xValues: number[] = new Array(SAMPLES);
    const yValues: number[] = new Array(SAMPLES);

    let maxY = 0;

    for (let i = 0; i < SAMPLES; i++) {
      const x = minX + i * step;
      const y = normalPDF(x);

      xValues[i] = x;
      yValues[i] = y;

      if (y > maxY) maxY = y;
    }

    maxY *= 1.1; // small margin for visibility

    return {
      title: {
        text: 'Distribution of Differences (B - A)',
        left: 'center',
        top: 0,
      },
      tooltip: { trigger: 'axis', confine: true },
      xAxis: { type: 'value', name: 'Difference' },
      yAxis: { type: 'value', name: 'Density' },
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
          lineStyle: { type: 'dashed', color: 'purple', width: 2 },
          showSymbol: false
        },
        {
          name: '+2σ',
          type: 'line',
          data: [[mean + 2 * std, 0], [mean + 2 * std, maxY]],
          lineStyle: { type: 'dashed', color: 'orange', width: 2 },
          showSymbol: false
        },
        {
          name: '-2σ',
          type: 'line',
          data: [[mean - 2 * std, 0], [mean - 2 * std, maxY]],
          lineStyle: { type: 'dashed', color: 'red', width: 2 },
          showSymbol: false
        }
      ],
      legend: {
        data: ['Difference PDF', 'Mean', '+2σ', '-2σ'],
        bottom: 0
      }
    };
  }

  private buildDifferenceHistogramChart():EChartsCoreOption {
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
        axisLabel: {rotate: 45}
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
          itemStyle: {color: 'steelblue'},
          barGap: 0,
          barCategoryGap: '0%'
        }
      ]
    };
  }

  private buildBoxplotChart():EChartsCoreOption {
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
          itemStyle: {color: 'lightblue'}
        }
      ]
    };
  }

}
