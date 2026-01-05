import {AfterViewInit, Component, Input, OnInit, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {provideEchartsCore} from 'ngx-echarts';
import {EChartsCoreOption} from 'echarts/core';
import { connect } from 'echarts/core';

// import echarts core
import * as echarts from 'echarts/core';
// import necessary echarts components
import {BarChart, HeatmapChart, CustomChart} from 'echarts/charts';
import {TooltipComponent, VisualMapComponent} from 'echarts/components';
import {GridComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {LegacyGridContainLabel} from 'echarts/features';
import {TitleComponent} from 'echarts/components'
import {DataZoomComponent} from 'echarts/components'
import {Subject} from 'rxjs';
import {ChunkedCell, ChunkingResult} from '../../dto/chunking';
import {FormsModule} from '@angular/forms';


echarts.use([TitleComponent, DataZoomComponent, LegacyGridContainLabel, TooltipComponent, VisualMapComponent, BarChart, GridComponent, CanvasRenderer, HeatmapChart, CustomChart]);


@Component({
  selector: 'app-heatmap',
  standalone: true,
  imports: [CommonModule,
    FormsModule],
  templateUrl: './heatmap.html',
  styleUrl: './heatmap.scss',
  providers: [
    provideEchartsCore({echarts})
  ]
})

export class Heatmap implements AfterViewInit {
  optionsLeft!: EChartsCoreOption;
  optionsRight!: EChartsCoreOption;

  chartElement1!: HTMLElement | null;
  chartElement2!: HTMLElement | null;
  chartInstance1!: echarts.ECharts;
  chartInstance2!: echarts.ECharts;

  rows: number = 100;
  cols: number = 100;
  groupSize: number = 1;
  @Input() comparisonId: number | null = null;

  cellsMatrix: string[][] = [];
  chunkedMatrix: string[][] = [];
  private groupSizeChange$ = new Subject<number>();

  @Input() data?: ChunkingResult; //fetch result from parent component

  constructor(
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["data"]?.currentValue){
      console.log("Heatmap received new data:", changes['data'].currentValue['chunked_cells']);
      this.updateHeatmaps(changes['data'].currentValue['chunked_cells']);
    }
  }



  ngAfterViewInit(): void {
    this.chartElement1 = document.getElementById('chart1');
    this.chartElement2 = document.getElementById('chart2');

    if (this.chartElement1 === null || this.chartElement2 === null) {
      alert("chart not found!");
    }
    this.chartInstance1 = echarts.init(this.chartElement1);
    this.chartInstance2 = echarts.init(this.chartElement2);


    this.optionsLeft = this.createHeatmapOptionsWithoutDataset([],"SetA", true);
    this.optionsRight = this.createHeatmapOptionsWithoutDataset( [],"SetB", false);

    this.chartInstance1.setOption(this.optionsLeft);
    this.chartInstance2.setOption(this.optionsRight);

    this.setHighlightBorderOnMouseover(this.chartInstance1, this.chartInstance2);
    this.connectHeatmaps();
  }

  private updateHeatmaps(matrix: ChunkedCell[][]) {
    if (!matrix || !Array.isArray(matrix) || matrix.length === 0) {
      console.warn("Received invalid heatmap data:", matrix);
      return;
    }

    const rows = matrix.length;
    const cols = matrix[0].length;
    console.log(`Updating chart with dimensions: ${rows}x${cols}`);

    const seriesDataA: any[] = [];
    const seriesDataB: any[] = [];
    let minX = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let minY = Number.POSITIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;

    for(let yIndex = 0; yIndex < rows; yIndex++) {
      for(let xIndex = 0; xIndex < cols; xIndex++) {
        const cell = matrix[yIndex][xIndex];
        if (!cell) continue;

        const x0 = Number(cell.x0);
        const x1 = Number(cell.x1);
        const y0 = Number(cell.y0);
        const y1 = Number(cell.y1);
        const valA = cell.veg_height_max_a ?? 0;
        const valB = cell.veg_height_max_b ?? 0;
        seriesDataA.push([x0, y0, x1, y1, valA]);
        seriesDataB.push([x0, y0, x1, y1, valB]);

        // Track bounding box for axes
        const localMinX = Math.min(x0, x1);
        const localMaxX = Math.max(x0, x1);
        const localMinY = Math.min(y0, y1);
        const localMaxY = Math.max(y0, y1);

        if (localMinX < minX) minX = localMinX;
        if (localMaxX > maxX) maxX = localMaxX;
        if (localMinY < minY) minY = localMinY;
        if (localMaxY > maxY) maxY = localMaxY;
      }
    }

    const renderItem = (params: any, api: any) => {
      const x0 = api.value(0);
      const y0 = api.value(1);
      const x1 = api.value(2);
      const y1 = api.value(3);
      const value = api.value(4);
      const sx = Math.min(x0, x1);
      const ex = Math.max(x0, x1);
      const sy = Math.min(y0, y1);
      const ey = Math.max(y0, y1);

      const p1 = api.coord([x0, y0]);
      const p2 = api.coord([x1, y1]);

      const pStart = api.coord([sx, sy]);
      const pEnd = api.coord([ex, ey]);

      const x = Math.min(pStart[0], pEnd[0]);
      const y = Math.min(pStart[1], pEnd[1]);
      const width = Math.abs(pEnd[0] - pStart[0]);
      const height = Math.abs(pEnd[1] - pStart[1]);

      // If width/height are zero (degenerate), return null to skip drawing
      if (width === 0 || height === 0) {
        return null;
      }

      return {
        type: 'rect',
        shape: { x, y, width, height },
        style: api.style({
          fill: api.visual('color'),
          stroke: '#444',
          lineWidth: 0.2
        })
      };
    };

    const seriesTemplate = {
      type: 'custom',
      renderItem: renderItem,
      encode: {
        x: 0,
        y: 1,
        // tooltip isn't strictly required here; we'll use params.data in tooltip formatter
      }
    };
    const axisUpdate = {
      xAxis: {
        min: minX,
        max: maxX,
        type: 'value',
        splitLine: { show: false }
      },
      yAxis: {
        min: minY,
        max: maxY,
        type: 'value',
        inverse: true,
        splitLine: { show: false }
      }
    };
    const tooltipFormatter = (params: any) => {
      // params.data is: [x0, y0, x1, y1, value]
      const d = params?.data;
      if (!d || d.length < 5) return '';
      const x0 = d[0], y0 = d[1], x1 = d[2], y1 = d[3], value = d[4];
      const sx = Math.min(x0, x1), ex = Math.max(x0, x1);
      const sy = Math.min(y0, y1), ey = Math.max(y0, y1);
      return `X: ${sx} - ${ex}<br/>Y: ${sy} - ${ey}<br/>Value: ${typeof value === 'number' ? value.toFixed(3) : value}`;
    };
    this.chartInstance1.setOption({
      ...axisUpdate,
      tooltip: {
        formatter: tooltipFormatter
      },
      series: [{
        ...seriesTemplate,
        data: seriesDataA
      }]
    });

    // Set options for right chart
    this.chartInstance2.setOption({
      ...axisUpdate,
      tooltip: {
        formatter: tooltipFormatter
      },
      series: [{
        ...seriesTemplate,
        data: seriesDataB
      }]
    });
  }

  private createHeatmapOptionsWithoutDataset(data: number[][], title: string, showVisualMap: boolean): EChartsCoreOption {
    return {
      title: {
        top: 0,
        text: title
      },
      tooltip: {
        position: 'top',
        formatter: (params: any) => {
          const [xIndex, yIndex, value] = params.value;
          const startX = xIndex * this.groupSize;
          const startY = yIndex * this.groupSize;
          return `X: ${startX} - ${startX + this.groupSize}<br/>` +
            `Y: ${startY} - ${startY + this.groupSize}<br/>` +
            `Value: ${value ? value.toFixed(2) : 'N/A'}`;
        },
        grid: {
          height: '75%',
          top: 70,
        },
      },
      xAxis: {
        type: 'value', // Changed from category
        min: 0,
        max: this.cols, // e.g., 100
        splitLine: { show: false }
      },
      yAxis: {
        type: 'value', // Changed from category
        min: 0,
        max: this.rows, // e.g., 100
        inverse: true, // Optional: makes Y=0 start at top like a matrix
        splitLine: { show: false }
      },
      dataZoom: [
        { type: 'slider', xAxisIndex: 0, bottom: 0, filterMode: 'none' },
        { type: 'slider', yAxisIndex: 0, orient: 'vertical', right: 0, filterMode: 'none' },
      ],
      visualMap: {
        min: 0,
        max: 30,
        calculable: true,
        orient: 'vertical',
        left: 0,
        top: "middle",
        inRange: {
          color: ['#e5f5e0', '#a6dba0', '#5aae61', '#1b7837', '#00441b'],
        },
        show: showVisualMap
      },
      series: [] // We will set this in updateHeatmaps
    };
  }





  private connectHeatmaps() {
    if (this.chartInstance1 && this.chartInstance2) {
      connect([this.chartInstance1, this.chartInstance2]);
    }
  }


  private setHighlightBorderOnMouseover(chart1: echarts.ECharts, chart2: echarts.ECharts) {
    chart1.on('mouseover', (params: any) => {
      if (params.seriesType !== 'heatmap') return;

      chart1.dispatchAction({
        type: 'highlight',
        seriesIndex: params.seriesIndex ?? 0,
        dataIndex: params.dataIndex,
      });
    });

    chart1.on('mouseout', (params: any) => {
      if (params.seriesType !== 'heatmap') return;

      chart1.dispatchAction({
        type: 'downplay',
        seriesIndex: params.seriesIndex ?? 0,
        dataIndex: params.dataIndex,
      });
    });


    chart2.on('mouseover', (params: any) => {
      if (params.seriesType !== 'heatmap') return;

      chart1.dispatchAction({
        type: 'highlight',
        seriesIndex: params.seriesIndex ?? 0,
        dataIndex: params.dataIndex,
      });
    });

    chart2.on('mouseout', (params: any) => {
      if (params.seriesType !== 'heatmap') return;

      chart1.dispatchAction({
        type: 'downplay',
        seriesIndex: params.seriesIndex ?? 0,
        dataIndex: params.dataIndex,
      });
    });

  }

}
