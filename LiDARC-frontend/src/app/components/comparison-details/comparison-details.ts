import {Component, Input, OnInit, signal, WritableSignal} from '@angular/core';
import {ComparisonService} from '../../service/comparison.service';
import {ActivatedRoute, RouterModule} from '@angular/router';
import {finalize, forkJoin} from 'rxjs';
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

export interface CellEntry {
  A: number;
  B: number;
  delta_z: number;
}

export interface Percentiles {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
}

export interface FileMetrics {
  mean: number;
  median: number;
  std: number;
  min: number;
  max: number;
  percentiles: Percentiles;
}

export interface DifferenceMetrics {
  mean: number;
  median: number;
  std: number;
  mostNegative: number;
  leastNegative: number;
  smallestPositive: number;
  largestPositive: number;
  pearsonCorrelation: number;
  percentiles: Percentiles;
}

export interface CategorizedCounts {
  "highly different": number;
  "almost equal": number;
  "slightly different": number;
  "different": number;
}

export interface VegetationStats {
  cells: CellEntry[];
  fileA_metrics: FileMetrics;
  fileB_metrics: FileMetrics;
  difference_metrics: DifferenceMetrics;
  categorized: CategorizedCounts;
}

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
    MatButton
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

  onScatterInit(ec: any): void {
    this.scatterInstance = ec;
  }

  //==== HARDCODED DATA====//
  vegetationStats: VegetationStats = {
    cells: [
      {A: 35.529, B: 38.77826, delta_z: 3.24926},
      {A: 40.822, B: 40.25174, delta_z: -0.57026},
      {A: 40.344, B: 43.637817, delta_z: 3.293817},
      {A: 41.517, B: 49.892883, delta_z: 8.375883},
      {A: 37.746, B: 35.852478, delta_z: -1.893522},
      {A: 38.451, B: 40.924164, delta_z: 2.473164},
      {A: 45.112, B: 48.547546, delta_z: 3.435546},
      {A: 42.307, B: 48.19464, delta_z: 5.88764},
      {A: 37.578, B: 42.959045, delta_z: 5.381045},
      {A: 39.579, B: 44.143494, delta_z: 4.564494},
      {A: 41.816, B: 40.91855, delta_z: -0.89745},
      {A: 39.84, B: 39.56314, delta_z: -0.27686},
      {A: 37.285, B: 42.292328, delta_z: 5.007328},
      {A: 38.396, B: 43.402985, delta_z: 5.006985},
      {A: 38.466, B: 44.12024, delta_z: 5.65424},
      {A: 49.576, B: 32.619324, delta_z: -16.956676},
      {A: 41.394, B: 42.70041, delta_z: 1.30641},
      {A: 38.745, B: 44.001892, delta_z: 5.256892},
      {A: 40.332, B: 43.125153, delta_z: 2.793153},
      {A: 38.655, B: 45.880127, delta_z: 7.225127},
      {A: 49.576, B: 26.165527, delta_z: -23.410473},
      {A: 49.576, B: 35.389618, delta_z: -14.186382},
      {A: 39.97, B: 43.42868, delta_z: 3.45868},
      {A: 40.804, B: 42.491272, delta_z: 1.687272},
      {A: 37.767, B: 43.123077, delta_z: 5.356077}
    ],
    fileA_metrics: {
      mean: 40.84732,
      median: 39.97,
      std: 3.829194331274748,
      min: 35.529,
      max: 49.576,
      percentiles: {
        p10: 37.6452,
        p25: 38.451,
        p50: 39.97,
        p75: 41.517,
        p90: 47.7904
      }
    },
    fileB_metrics: {
      mean: 41.6961756,
      median: 42.959045,
      std: 5.0919100436313185,
      min: 26.165527,
      max: 49.892883,
      percentiles: {
        p10: 35.574762,
        p25: 40.25174,
        p50: 42.959045,
        p75: 44.001892,
        p90: 47.2688348
      }
    },
    difference_metrics: {
      mean: 0.8488556,
      median: 3.293817,
      std: 7.72577964368323,
      mostNegative: -23.410473,
      leastNegative: -0.27686,
      smallestPositive: 1.30641,
      largestPositive: 8.375883,
      pearsonCorrelation: -0.48972918052165376,
      percentiles: {
        p10: -9.269238,
        p25: -0.27686,
        p50: 3.293817,
        p75: 5.256892,
        p90: 5.79428
      }
    },
    categorized: {
      "highly different": 12,
      "almost equal": 6,
      "slightly different": 6,
      "different": 1
    }
  };


  scatterOption!: EChartsCoreOption;
  fileDistributionOption!: EChartsCoreOption;
  diffDistributionOption!: EChartsCoreOption;
  diffHistogramOption !: EChartsCoreOption;
  categoryBarChart !: EChartsCoreOption;
  boxplotOption!: EChartsCoreOption;


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

            //====HARDCODED ECHARTS OPTIONS====//
            //TODO clarify if all of this vis are needed/or more/other visualizations are preferred by BFW?
            this.scatterOption = this.buildScatterChart();
            this.fileDistributionOption = this.buildDistributionChart();
            this.diffDistributionOption = this.buildDifferenceDistributionChart();
            this.diffHistogramOption = this.buildDifferenceHistogramChart();
            this.categoryBarChart = this.buildCategoryBarChart();
            this.boxplotOption = this.buildBoxplotChart();

          },
          error: (error) => {
            console.error('Error fetching comparison data:', error);
            this.errorMessage.set('Failed to fetch comparison data. Please try again later.');
          }
        });
    }
  }

  loadMoreReports(): void {
    if (this.comparisonId == null) return;

    this.reportsLimit += 5;
    this.fetchReports();
  }

  createReport(): void {
    const chartImages: ChartData[] = [];
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

  dataURItoBlob(dataURI: string): Blob {
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

  //=====HARDCODED ECHARTS OPTIONS====//
  private buildScatterChart(): EChartsCoreOption {
    const scatterData = this.vegetationStats.cells.map(c => [c.A, c.B]);

    // Linear regression TODO should be done in worker
    const n = scatterData.length;
    const sumX = scatterData.reduce((s, [x]) => s + x, 0);
    const sumY = scatterData.reduce((s, [, y]) => s + y, 0);
    const sumXY = scatterData.reduce((s, [x, y]) => s + x * y, 0);
    const sumX2 = scatterData.reduce((s, [x]) => s + x * x, 0);
    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    const xVals = scatterData.map(([x]) => x);
    const minX = Math.min(...xVals);
    const maxX = Math.max(...xVals);
    const yVals = scatterData.map(([, y]) => y);
    const minY = Math.min(...yVals);
    const maxY = Math.max(...yVals);

    const lineData = [
      [minX, slope * minX + intercept],
      [maxX, slope * maxX + intercept]
    ];

    return {
      title: {
        show: true,
        text: 'Vegetation Height Correlation',
        subtext: `Pearson r = ${this.vegetationStats.difference_metrics.pearsonCorrelation.toFixed(2)}`,
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
        type: 'value',
        min: Math.floor(minX - 1),
        max: Math.ceil(maxX + 1)
      },
      yAxis: {
        name: 'veg_height_B',
        type: 'value',
        min: Math.floor(minY - 1),
        max: Math.ceil(maxY + 1)
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

  private buildDistributionChart(): EChartsCoreOption {
    const {fileA_metrics, fileB_metrics} = this.vegetationStats;

    // Helper function: PDF of normal distribution
    const normalPDF = (x: number, mean: number, std: number) =>
      (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));

    // Define the range of x (min to max of both files)
    const minX = Math.floor(Math.min(fileA_metrics.min, fileB_metrics.min) - 5);
    const maxX = Math.ceil(Math.max(fileA_metrics.max, fileB_metrics.max) + 10);

    const step = (maxX - minX) / 100;
    const xValues = [];
    for (let x = minX; x <= maxX; x += step) xValues.push(x);

    // Compute y-values for both distributions
    const fileA_Y = xValues.map(x => normalPDF(x, fileA_metrics.mean, fileA_metrics.std));
    const fileB_Y = xValues.map(x => normalPDF(x, fileB_metrics.mean, fileB_metrics.std));

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
        name: 'Vegetation Height',
        min: minX,
        max: maxX
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
          lineStyle: {width: 2, color: 'blue'}
        },
        {
          name: 'File B',
          type: 'line',
          smooth: true,
          data: xValues.map((x, i) => [x, fileB_Y[i]]),
          lineStyle: {width: 2, color: 'red'}
        }
      ]
    };
  }

  private buildDifferenceDistributionChart(): EChartsCoreOption {
    const differences = this.vegetationStats.cells.map(c => c.B - c.A);

    // Compute mean and std
    const n = differences.length;
    const mean = differences.reduce((s, x) => s + x, 0) / n;
    const std = Math.sqrt(differences.reduce((s, x) => s + (x - mean) ** 2, 0) / n);

    // Normal PDF function
    const normalPDF = (x: number) =>
      (1 / (std * Math.sqrt(2 * Math.PI))) * Math.exp(-0.5 * Math.pow((x - mean) / std, 2));

    // X-axis values
    const minX = Math.floor(Math.min(...differences) - 1);
    const maxX = Math.ceil(Math.max(...differences) + 17);
    const step = (maxX - minX) / 100;
    const xValues = [];
    for (let x = minX; x <= maxX; x += step) xValues.push(x);

    // PDF values
    const yValues = xValues.map(x => normalPDF(x));

    return {
      title: {
        text: 'Distribution of Differences (B - A)',
        left: 'center',
        top: 0,
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
      },
      xAxis: {
        type: 'value',
        name: 'Difference',
        min: minX,
        max: maxX
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
          lineStyle: {width: 2, color: 'green'},
          markLine: {
            symbol: 'none',
            label: {
              show: true,
              position: 'end',
              formatter: (param: any) => `${param.name}: ${param.value.toFixed(2)}`
            },
            data: [
              {name: 'Mean', xAxis: mean},
              {name: '+2σ', xAxis: mean + 2 * std},
              {name: '-2σ', xAxis: mean - 2 * std}
            ],
            tooltip: {
              formatter: (param: any) => `${param.name}: ${param.value.toFixed(2)}`
            }
          }
        }
      ],
      legend: {
        data: ['Difference PDF'],
        bottom: 0
      }
    };
  }

  private buildDifferenceHistogramChart(): EChartsCoreOption {
    const differences = this.vegetationStats.cells.map(c => c.B - c.A);
    //TODO if histogram wanted --> calculate bins in worker
    // Histogram parameters
    const bins = 10;
    const minX = Math.min(...differences);
    const maxX = Math.max(...differences);
    const binWidth = (maxX - minX) / bins;

    // Compute counts per bin
    const counts = new Array(bins).fill(0);
    differences.forEach(d => {
      let idx = Math.floor((d - minX) / binWidth);
      if (idx >= bins) idx = bins - 1;
      counts[idx]++;
    });

    // X-axis labels: bin ranges as strings
    const binLabels = [];
    for (let i = 0; i < bins; i++) {
      const start = (minX + i * binWidth).toFixed(2);
      const end = (minX + (i + 1) * binWidth).toFixed(2);
      binLabels.push(`${start} - ${end}`);
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

  private buildCategoryBarChart(): EChartsCoreOption {
    //TODO check if needed: if yes calculate in worker
    const categorized = this.vegetationStats.categorized;
    const categories = Object.keys(categorized);
    const counts = Object.values(categorized);

    return {
      title: {
        text: 'Counts per Category',
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
      graphic: {
        show: true,
        type: 'group',
        right: 10,
        top: 50,
        children: [
          {
            type: 'text',
            style: {
              text:
                'Category Ranges:\n' +
                '• almost equal:          0–2\n' +
                '• slightly different:    2–4\n' +
                '• different:             4–5\n' +
                '• highly different:      >5',
              fontSize: 12,
              fontFamily: 'Arial',
              fill: '#444',
              lineHeight: 18,
              align: 'center'
            }
          }
        ]
      },
      xAxis: {
        type: 'category',
        name: 'Category',
        data: categories,
        axisLabel: {rotate: 30}
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
          itemStyle: {color: 'orange'},
          barGap: 0,
          barCategoryGap: '50%'
        }
      ]
    };
  }

  private buildBoxplotChart(): EChartsCoreOption {
    const {fileA_metrics, fileB_metrics} = this.vegetationStats;

    const data = [
      {
        value: [
          fileA_metrics.min,
          fileA_metrics.percentiles.p25,
          fileA_metrics.median,
          fileA_metrics.percentiles.p75,
          fileA_metrics.max
        ],
        tooltipValue: fileA_metrics
      },
      {
        value: [
          fileB_metrics.min,
          fileB_metrics.percentiles.p25,
          fileB_metrics.median,
          fileB_metrics.percentiles.p75,
          fileB_metrics.max
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
            Min: ${metrics.min}<br>
            Q1: ${metrics.percentiles.p25}<br>
            Median: ${metrics.median}<br>
            Q3: ${metrics.percentiles.p75}<br>
            Max: ${metrics.max}`;
        }
      },
      xAxis: {
        type: 'category',
        data: categories
      },
      yAxis: {
        type: 'value',
        name: 'Vegetation Height',
        min: 25
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
