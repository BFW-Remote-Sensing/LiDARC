import {AfterViewInit, Component, ElementRef, Input, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {provideEchartsCore} from 'ngx-echarts';
import {EChartsCoreOption} from 'echarts/core';
import {connect} from 'echarts/core';

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
import {max, Subject} from 'rxjs';
import {ChunkedCell, ChunkingResult} from '../../dto/chunking';
import {FormsModule} from '@angular/forms';
import {MatMenu, MatMenuItem, MatMenuTrigger,} from '@angular/material/menu';
import {MatDivider} from '@angular/material/divider';
import {MatIcon} from '@angular/material/icon';
import {MatButton} from '@angular/material/button';
import {MatButtonToggle, MatButtonToggleGroup} from '@angular/material/button-toggle';


echarts.use([TitleComponent, DataZoomComponent, LegacyGridContainLabel, TooltipComponent, VisualMapComponent, BarChart, GridComponent, CanvasRenderer, HeatmapChart, CustomChart]);

type Mode = 'AB' | 'D' | 'ABD';
type SchemeKey = "greens" | "browns" | "deltas";


@Component({
  selector: 'app-heatmap',
  standalone: true,
  imports: [CommonModule,
    FormsModule, MatButtonToggleGroup, MatButtonToggle, MatIcon, MatButton, MatMenu, MatMenuTrigger, MatMenuItem],
  templateUrl: './heatmap.html',
  styleUrl: './heatmap.scss',
  providers: [
    provideEchartsCore({echarts})
  ]
})

export class Heatmap implements AfterViewInit {
  mode: Mode = 'ABD'

  // --- UI helpers ---
  get showAB(): boolean {
    return this.mode === 'AB' || this.mode === 'ABD';
  }

  get showD(): boolean {
    return this.mode === 'D' || this.mode === 'ABD';
  }

  get displayedHeatmap(): string {
    if (this.mode === 'AB') return 'ab';
    if (this.mode === 'D') return 'd';
    return 'abd';
  }
  private readonly COLOR_Schemes: Record<SchemeKey, { label: string; color: string[] }> = {
    greens: {
      label: 'Greens',
      color: ['#e5f5e0', '#a6dba0', '#5aae61', '#1b7837', '#00441b'],
    },
    browns: {
      label: 'Browns',
      color: ['#8c510a', '#d8b365', '#f6e8c3', '#c7eae5', '#5ab4ac', '#01665e'],
    },
    deltas: {
      label: 'Blue',
      color: ['#2166ac', '#67a9cf','#f7f7f7','#ef8a62','#b2182b']
    }
  };


  private readonly GROUP_AB = 'group-ab';
  private readonly GROUP_ABD = 'group-abd';

  showVisualMap = true;
  showZoom = true;
  selectedColorScheme: SchemeKey = "greens"; //default color scheme


  optionsLeft!: EChartsCoreOption;
  optionsRight!: EChartsCoreOption;
  differenceOptions!: EChartsCoreOption;


  chartInstance1!: echarts.ECharts;
  chartInstance2!: echarts.ECharts;
  differenceInstance!: echarts.ECharts;
  @ViewChild("chart1") chart1?: ElementRef<HTMLDivElement>;
  @ViewChild("chart2") chart2?: ElementRef<HTMLDivElement>;
  @ViewChild("diffChart") diffElement?: ElementRef<HTMLDivElement>;


  rows: number = 100;
  cols: number = 100;
  groupSize: number = 1;
  @Input() comparisonId: number | null = null;

  cellsMatrix: string[][] = [];
  chunkedMatrix: string[][] = [];
  private groupSizeChange$ = new Subject<number>();

  @Input() data?: ChunkingResult; //fetch result from parent component

  constructor() {
  }


  ngOnChanges(changes: SimpleChanges): void {
    if (changes["data"]?.currentValue) {
      console.log("Heatmap received new data:", changes['data'].currentValue['chunked_cells']);
      this.updateHeatmaps(changes['data'].currentValue['chunked_cells']);
    }
  }


  ngAfterViewInit(): void {

    if (this.chart1 === null || this.chart2 === null || this.diffElement === null) {
      alert("chart not found!");
      return
    }
    this.chartInstance1 = echarts.init(this.chart1?.nativeElement);
    this.chartInstance2 = echarts.init(this.chart2?.nativeElement);
    this.differenceInstance = echarts.init(this.diffElement?.nativeElement);


    this.optionsLeft = this.createHeatmapOptionsWithoutDataset("SetA", true);
    this.optionsRight = this.createHeatmapOptionsWithoutDataset("SetB", false);
    this.differenceOptions = this.createDeltaHeatmapOptions("Delta_z", true);


    this.chartInstance1.setOption(this.optionsLeft);
    this.chartInstance2.setOption(this.optionsRight);
    this.differenceInstance.setOption(this.differenceOptions);

    this.setupHoverInteractions();

    // connect initial mode
    //this.applyConnections();

  }

  get selectedSchemeLabel():string{
    return this.COLOR_Schemes[this.selectedColorScheme].label;
  }

  setScheme(key: SchemeKey): void{
    this.selectedColorScheme = key;
    console.log(key);
    console.log(this.selectedColorScheme);
    console.log(this.COLOR_Schemes[this.selectedColorScheme].color);
    this.applyColorScheme();
  }

  private applyColorScheme(): void {
    // const colors = this.COLOR_Schemes[this.selectedColorScheme].color;
    // // Update visual maps with new colors
    // const opt = {
    //   ...this.BASE_VirtualMap
    // };
    // this.updateVisualMap(this.chartInstance1,true, false);
    // this.updateVisualMap(this.chartInstance2,false, false);
  //
  //   this.chartInstance1?.setOption(opt, {replaceMerge: ['visualMap'] as any});
  //   this.chartInstance2?.setOption(opt, {replaceMerge: ['visualMap'] as any});
    const colors = this.COLOR_Schemes[this.selectedColorScheme].color;
    console.log("apply" + colors);

    const opt = {
      visualMap: [{
        ...this.BASE_VisualMap,
        show: this.showVisualMap,
        inRange: { color: colors }
      }]
    };

    const opt2 = {
      visualMap: [{
        ...this.BASE_VisualMap,
        show: false, //we never want to show the legend for chart2
        inRange: { color: colors }
      }]
    };

    this.chartInstance1?.setOption(opt, { replaceMerge: ['visualMap'] as any });
    this.chartInstance2?.setOption(opt2, { replaceMerge: ['visualMap'] as any });
   }

  setMode(mode: Mode): void {
    this.mode = mode;

    // update connections based on current mode
    //this.applyConnections();
  }


  private applyConnections(): void {
    // Alles “entgruppen” (wichtig, sonst hängen alte Verbindungen)
    // this.chartInstance1.group = '';
    // this.chartInstance2.group = '';
    // this.differenceInstance.group = '';

    // Vorherige Gruppen trennen
    echarts.disconnect(this.GROUP_AB);
    echarts.disconnect(this.GROUP_ABD);

    // Je nach Mode neue Gruppe setzen + connect
    if (this.mode === 'AB') {
      this.chartInstance1.group = this.GROUP_AB;
      this.chartInstance2.group = this.GROUP_AB;
      echarts.connect(this.GROUP_AB);
    }

    if (this.mode === 'ABD') {
      this.chartInstance1.group = this.GROUP_ABD;
      this.chartInstance2.group = this.GROUP_ABD;
      this.differenceInstance.group = this.GROUP_ABD;
      echarts.connect(this.GROUP_ABD);
    }

    // mode === 'D' -> keine Verbindung nötig
  }


  // toggleVisualMap(): void {
  //   this.showVisualMap = !this.showVisualMap;
  //   this.applyVisualMapVisibility();
  // }

  toggleZoom(): void {
    this.showZoom = !this.showZoom;
    this.applyZoomVisibility();
  }

  private updateVisualMap(chart: echarts.ECharts, show: boolean, delta_chart: boolean) {
    let option;
    let colors;
    delta_chart ? option = this.BASE_DeltaVirtualMap : option = this.BASE_VisualMap;
    delta_chart ? colors = this.COLOR_Schemes.deltas.color : colors = this.COLOR_Schemes[this.selectedColorScheme].color;

    console.log("updateVisualMap" + colors);

    const opt = {
      visualMap: [
        {
          ...option,
          inRange: {
            color: colors
          },
          show: show
        }
      ]
    }
    chart?.setOption(opt, { replaceMerge: ['visualMap'] as any });


    // chart.setOption(
    //   {
    //     visualMap: [
    //       {
    //         ...option,
    //         color: colors,
    //         show: show
    //       }
    //     ]
    //   },
    //   {replaceMerge: ['visualMap'] as any}
    // );
  }

  toggleVisualMap(): void {
    this.showVisualMap = !this.showVisualMap;
    console.log("Toggling visual map to " + this.showVisualMap);

    if (this.showAB) {
      this.updateVisualMap(this.chartInstance1, this.showVisualMap, false);
      //this.updateVisualMap(this.chartInstance2, this.showVisualMap, false);
    }
    if (this.showD) {
      this.updateVisualMap(this.differenceInstance, this.showVisualMap, true);
    }
  }

  private applyZoomVisibility(): void {
    const show = this.showZoom;

    // {type: 'slider', xAxisIndex: 0, bottom: 0, filterMode: 'none'},
    // {type: 'slider', yAxisIndex: 0, orient: 'vertical', right: 0, filterMode: 'none'},

    const opt = {
      dataZoom: [
        { // x slider
          type: 'slider',
          xAxisIndex: 0,
          bottom: 0,
          filterMode: 'none',
          show,
          //height: show ? 20 : 0
        },
        { // y slider
          type: 'slider',
          yAxisIndex: 0,
          orient: 'vertical',
          right: 0,
          filterMode: 'none',
          show,
          //width: show ? 20 : 0
        }
      ]
    };

    this.chartInstance1?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
    this.chartInstance2?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
    this.differenceInstance?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
  }


  private createDeltaHeatmapOptions(title: string, showVisualMap: boolean): EChartsCoreOption {
    const options = this.createHeatmapOptionsWithoutDataset(title, showVisualMap);
    const visMap = {
      visualMap: this.BASE_DeltaVirtualMap
    }
    return {...options, ...visMap};
  }

  /*
    private updateHeatmapRevision(matrix: ChunkedCell[][]) {
      if (!matrix || !Array.isArray(matrix) || matrix.length === 0) {
        console.warn("Received invalid heatmap data:", matrix);
        return;
      }

      this.rows = matrix.length;
      this.cols = Math.max(...matrix.map(row => row.length));
      //const cols = matrix[0].length;
      console.log(`Updating chart with dimensions: ${this.rows}x${this.cols}`);

      const seriesDataA: any[] = [];
      const seriesDataB: any[] = [];
      let minX = Number.POSITIVE_INFINITY;
      let maxX = Number.NEGATIVE_INFINITY;
      let minY = Number.POSITIVE_INFINITY;
      let maxY = Number.NEGATIVE_INFINITY;

      for(let yIndex = 0; yIndex < this.rows; yIndex++) {
        for(let xIndex = 0; xIndex < this.cols; xIndex++) {
          const cell = matrix[yIndex][xIndex];
          if (!cell) continue;

          const xi = xIndex;
          const yi = yIndex;

          const x0 = Number(cell.x0);
          const x1 = Number(cell.x1);
          const y0 = Number(cell.y0);
          const y1 = Number(cell.y1);
          const valA = cell.veg_height_max_a ?? 0;
          const valB = cell.veg_height_max_b ?? 0;
          seriesDataA.push([xi, yi, valA, x0, y0, x1, y1]);
          seriesDataB.push([xi, yi, valB, x0, y0, x1, y1]);

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
      console.log(seriesDataA);
      console.log(seriesDataB);

      this.setEchartsOption(seriesDataA, seriesDataB, minX,minY,maxX,maxY);
    }

    private setEchartsOption(seriesDataA: any[], seriesDataB: any[], minX: number, minY: number, maxX: number, maxY: number ) {
      // const seriesTemplate = {
      //   type: 'heatmap',
      //   encode: {
      //     x: 0,
      //     y: 1,
      //     value: 2
      //     // tooltip isn't strictly required here; we'll use params.data in tooltip formatter
      //   }
      // };
      // const axisUpdate = {
      //   xAxis: {
      //     min: minX,
      //     max: maxX,
      //     type: 'value',
      //     splitLine: { show: false }
      //   },
      //   yAxis: {
      //     min: minY,
      //     max: maxY,
      //     type: 'value',
      //     inverse: true,
      //     splitLine: { show: false }
      //   }
      // };

      const tooltipFormatter = (params: any) => {
        // params.data is: [xi, yi, value, x0, y0, x1, y1]
        const d = params?.data;
        if (!d || d.length < 5) return '';
        const xi = d[0], yi = d[1], value = d[2];
        const x0 = d[3], y0 = d[4], x1 = d[5], y1 = d[6];
        const sx = Math.min(x0, x1), ex = Math.max(x0, x1);
        const sy = Math.min(y0, y1), ey = Math.max(y0, y1);
        return `X: ${sx} - ${ex}<br/>Y: ${sy} - ${ey}<br/>Value: ${typeof value === 'number' ? value.toFixed(3) : value}`;
      };
      this.chartInstance1.setOption({
        //...axisUpdate,
        tooltip: {
          formatter: tooltipFormatter
        },
        series: [{
          //...seriesTemplate,
          data: seriesDataA
        }]
      });
      this.chartInstance2.setOption({
        //...axisUpdate,
        tooltip:
          formatter: tooltipFormatter
        },
        series: [{
         // ...seriesTemplate,
          data: seriesDataB
        }]
      })
    }

  */
  private updateHeatmaps(matrix: ChunkedCell[][]) {
    if (!matrix || !Array.isArray(matrix) || matrix.length === 0) {
      console.warn("Received invalid heatmap data:", matrix);
      return;
    }

    this.rows = matrix.length;
    this.cols = Math.max(...matrix.map(row => row.length));
    console.log(`Updating chart with dimensions: ${this.rows}x${this.cols}`);

    const seriesDataA: any[] = [];
    const seriesDataB: any[] = [];
    const differenceData: any[] = [];
    let minX = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let minY = Number.POSITIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;

    for (let yIndex = 0; yIndex < this.rows; yIndex++) {
      for (let xIndex = 0; xIndex < this.cols; xIndex++) {
        const cell = matrix[yIndex][xIndex];
        if (!cell) continue;

        const x0 = Number(cell.x0);
        const x1 = Number(cell.x1);
        const y0 = Number(cell.y0);
        const y1 = Number(cell.y1);
        const valA = cell.veg_height_max_a ?? 0;
        const valB = cell.veg_height_max_b ?? 0;
        const delta_z = cell.delta_z ?? 0;
        seriesDataA.push([x0, y0, x1, y1, valA]);
        seriesDataB.push([x0, y0, x1, y1, valB]);
        differenceData.push([x0, y0, x1, y1, delta_z]);

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
    console.log(differenceData)
    console.log(seriesDataA);
    console.log(seriesDataB);

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

      const rectShape = { x, y, width, height };

      // ✅ Clip gegen den sichtbaren Plotbereich
      // (Verhindert das Zeichnen außerhalb des sichtbaren Bereichs) bei data Zoom
      const clipped = echarts.graphic.clipRectByRect(rectShape, params.coordSys);

      // Wenn komplett außerhalb -> nicht zeichnen
      if (!clipped) return null;

      return {
        type: 'rect',
        shape: clipped,
        style: api.style({
          fill: api.visual('color'),
          stroke: '#444',
          lineWidth: 0.2
        }),
        emphasis: {
          style: { stroke: '#000', lineWidth: 1.2 }
        }
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
        splitLine: {show: false}
      },
      yAxis: {
        min: minY,
        max: maxY,
        type: 'value',
        inverse: true,
        splitLine: {show: false}
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
        trigger: "item",
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
        trigger: "item",
        formatter: tooltipFormatter
      },
      series: [{
        ...seriesTemplate,
        data: seriesDataB
      }]
    });

    this.differenceInstance.setOption({
      ...axisUpdate,
      tooltip: {
        trigger: "item",
        formatter: tooltipFormatter
      },
      series: [{
        ...seriesTemplate,
        data: differenceData
      }]
    });
  }


  private createHeatmapOptionsWithoutDataset(title: string, showVisualMap: boolean): EChartsCoreOption {
    return {
      title: {
        top: 0,
        text: title
      },
      xAxis: {
        type: 'value', // Changed from category
        min: 0,
        max: this.cols, // e.g., 100
        splitLine: {show: false}
      },
      yAxis: {
        type: 'value', // Changed from category
        min: 0,
        max: this.rows, // e.g., 100
        inverse: true, // Optional: makes Y=0 start at top like a matrix
        splitLine: {show: false}
      },
      dataZoom: [
        {type: 'slider', xAxisIndex: 0, bottom: 0, filterMode: 'none'},
        {type: 'slider', yAxisIndex: 0, orient: 'vertical', right: 0, filterMode: 'none'}
      ],
      visualMap: [{
        ...this.BASE_VisualMap,
        show: showVisualMap
      }],// We will set the rest in updateHeatmaps
      series: [
        {
          type: 'custom',
        }
      ]
    };
  }

  private setupHoverInteractions(): void {
    const isSeriesItem = (p: any) =>
      p && p.componentType === 'series' && p.dataIndex != null;

    const bind = (source: echarts.ECharts, targets: echarts.ECharts[]) => {
      // Smooth hover sync (tooltip + highlight)
      source.on('mousemove', (p: any) => {
        if (!isSeriesItem(p)) return;

        for (const t of targets) {
          t.dispatchAction({
            type: 'showTip',
            seriesIndex: p.seriesIndex ?? 0,
            dataIndex: p.dataIndex
          });

          t.dispatchAction({
            type: 'highlight',
            seriesIndex: p.seriesIndex ?? 0,
            dataIndex: p.dataIndex
          });
        }
      });

      // Clear on leaving an item
      source.on('mouseout', (p: any) => {
        if (!isSeriesItem(p)) return;

        for (const t of targets) {
          t.dispatchAction({ type: 'hideTip' });
          t.dispatchAction({
            type: 'downplay',
            seriesIndex: p.seriesIndex ?? 0,
            dataIndex: p.dataIndex
          });
        }
      });

      // Clear on leaving the chart completely
      source.on('globalout', () => {
        for (const t of targets) {
          t.dispatchAction({ type: 'hideTip' });
          // downplay all in series 0 is usually fine; adjust if you have multiple series
          t.dispatchAction({ type: 'downplay', seriesIndex: 0 });
        }
      });
    };

    // A ↔ B
    //bind(this.chartInstance1, [this.chartInstance2]);
    //bind(this.chartInstance2, [this.chartInstance1]);

    // If you want A/B hover to also affect Delta:
     bind(this.chartInstance1, [this.chartInstance2, this.differenceInstance]);
     bind(this.chartInstance2, [this.chartInstance1, this.differenceInstance]);
     bind(this.differenceInstance, [this.chartInstance1, this.chartInstance2]);
  }






  // private setHighlightBorderOnMouseover(chart1: echarts.ECharts, chart2: echarts.ECharts) {
  //   chart1.on('mouseover', (params: any) => {
  //     if (params.seriesType !== 'heatmap') return;
  //
  //     chart1.dispatchAction({
  //       type: 'highlight',
  //       seriesIndex: params.seriesIndex ?? 0,
  //       dataIndex: params.dataIndex,
  //     });
  //   });
  //
  //   chart1.on('mouseout', (params: any) => {
  //     if (params.seriesType !== 'heatmap') return;
  //
  //     chart1.dispatchAction({
  //       type: 'downplay',
  //       seriesIndex: params.seriesIndex ?? 0,
  //       dataIndex: params.dataIndex,
  //     });
  //   });
  //
  //
  //   chart2.on('mouseover', (params: any) => {
  //     if (params.seriesType !== 'heatmap') return;
  //
  //     chart1.dispatchAction({
  //       type: 'highlight',
  //       seriesIndex: params.seriesIndex ?? 0,
  //       dataIndex: params.dataIndex,
  //     });
  //   });
  //
  //   chart2.on('mouseout', (params: any) => {
  //     if (params.seriesType !== 'heatmap') return;
  //
  //     chart1.dispatchAction({
  //       type: 'downplay',
  //       seriesIndex: params.seriesIndex ?? 0,
  //       dataIndex: params.dataIndex,
  //     });
  //   });
  //
  // }

  private setupHoverSync(): void {
    const link = (source: echarts.ECharts, targets: echarts.ECharts[]) => {
      // Smooth sync while moving
      source.on('mousemove', (p: any) => {
        if (p?.dataIndex == null) return;

        for (const t of targets) {
          t.dispatchAction({
            type: 'showTip',
            seriesIndex: p.seriesIndex ?? 0,
            dataIndex: p.dataIndex
          });
          t.dispatchAction({
            type: 'highlight',
            seriesIndex: p.seriesIndex ?? 0,
            dataIndex: p.dataIndex
          });
        }
      });

      // Leaving an item
      source.on('mouseout', () => {
        for (const t of targets) {
          t.dispatchAction({ type: 'hideTip' });
          t.dispatchAction({ type: 'downplay', seriesIndex: 0 });
        }
      });

      // Leaving the whole chart area
      source.on('globalout', () => {
        for (const t of targets) {
          t.dispatchAction({ type: 'hideTip' });
          t.dispatchAction({ type: 'downplay', seriesIndex: 0 });
        }
      });
    };

    // A ↔ B hover sync (zoom remains independent because no connect())
    link(this.chartInstance1, [this.chartInstance2]);
    link(this.chartInstance2, [this.chartInstance1]);

    // Optional: also sync to delta in ABD mode
    // link(this.chartInstance1, [this.differenceInstance]);
    // link(this.chartInstance2, [this.differenceInstance]);
    // link(this.differenceInstance, [this.chartInstance1, this.chartInstance2]);
  }


  private BASE_VisualMap: any = {
    min: 0,
    max: 30,
    calculable: true,
    orient: 'vertical',
    left: -5,
    top: "middle",
    text: [],   // Beschriftung
    textGap: -5,              // Abstand Text ↔ Farbskala
    inRange: {
      color: this.COLOR_Schemes[this.selectedColorScheme].color //does not change dynamically, has to be set manually
    }
  }
  private BASE_DeltaVirtualMap = {
      type: 'continuous',
      min: -10,
      max: 10,
      orient: 'vertical',
      left: 0,
      top: "middle",
      calculable: true,
      inRange: {
        color: this.COLOR_Schemes.deltas.color
      }
    }




}

