import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  booleanAttribute , Output, EventEmitter,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {provideEchartsCore} from 'ngx-echarts';
import {EChartsCoreOption} from 'echarts/core';
import {connect} from 'echarts/core';

// import echarts core
import * as echarts from 'echarts/core';
// import necessary echarts components
import {BarChart, CustomChart, HeatmapChart} from 'echarts/charts';
import {
  DataZoomComponent,
  GridComponent,
  TitleComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {LegacyGridContainLabel} from 'echarts/features';
import { Subject} from 'rxjs';
import {ChunkedCell, ChunkingResult} from '../../dto/chunking';
import {FormsModule} from '@angular/forms';
import {MatMenu, MatMenuItem, MatMenuTrigger,} from '@angular/material/menu';
import {MatIcon} from '@angular/material/icon';
import {MatButton} from '@angular/material/button';
import {MatButtonToggle, MatButtonToggleGroup} from '@angular/material/button-toggle';
import {MatCheckbox} from '@angular/material/checkbox';
import {MatTooltip} from '@angular/material/tooltip';


echarts.use([TitleComponent, DataZoomComponent, LegacyGridContainLabel, TooltipComponent, VisualMapComponent, BarChart, GridComponent, CanvasRenderer, HeatmapChart, CustomChart]);

type Mode = 'AB' | 'D' | 'ABD';
type SchemeKey = "greens" | "browns" | "deltas";


@Component({
  selector: 'app-heatmap',
  standalone: true,
  imports: [CommonModule,
    FormsModule, MatButtonToggleGroup, MatButtonToggle, MatIcon, MatButton, MatMenu, MatMenuTrigger, MatMenuItem, MatCheckbox, MatTooltip],
  templateUrl: './heatmap.html',
  styleUrl: './heatmap.scss',
  providers: [
    provideEchartsCore({echarts})
  ]
})

export class Heatmap implements AfterViewInit, OnChanges {
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
  private readonly GROUP_AB = 'group-ab';
  private readonly GROUP_ABD = 'group-abd';

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
      label: 'Deltas',
      color: ['#2166ac', '#67a9cf','#f7f7f7','#ef8a62','#b2182b']
    }
  };
  selectedColorScheme: SchemeKey = "greens"; //default color scheme



  @Input() groupMapping!: {a: string, b: string};
  @Input({transform: booleanAttribute}) needOutlierDetection: boolean = false;
  @Input() outlierDeviationFactor: number | undefined;
  @Input() comparisonId: number | null = null;
  @Input() max_veg_height_a: number | null = 30;
  @Input() min_veg_height_a: number | null = 0;
  @Input() min_veg_height_b: number | null = 0;
  @Input() max_veg_height_b: number | null = 30;

  @Input() data?: ChunkingResult; //fetch result from parent component

  @Input() showOutliers = true;
  @Output() showOutliersChange = new EventEmitter<boolean>();

  // onOutlierToggle(e: MatCheckboxChange) {
  //   this.showOutliersChange.emit(e.checked);
  // }

  showVisualMap = true;
  showZoom = false;
  heatmapsStackedLayout = false;

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
  groupSize: number = 5;
  cellsMatrix: string[][] = [];
  chunkedMatrix: string[][] = [];


  private groupSizeChange$ = new Subject<number>();
  private _showPercentiles = false;
  @Input()
  set showPercentiles(val:boolean) {
    this._showPercentiles = val;
    if (this.data?.chunked_cells) {
      this.updateHeatmaps(this.data.chunked_cells);
    }
  }
  get showPercentiles() {
    return this._showPercentiles;
  }




  constructor() {
  }



  ngOnChanges(changes: SimpleChanges): void {
    if (changes["data"]?.currentValue) {
      console.log("Heatmap received new data:", changes['data'].currentValue['chunked_cells']);
      this.updateHeatmaps(changes['data'].currentValue['chunked_cells']);
    }
    if (changes["showOutliers"]?.currentValue !== changes["showOutliers"]?.previousValue && this.data) {
      console.log("Outlier visibility toggled, rebuilding heatmaps...");
      this.updateHeatmaps(this.data['chunked_cells']);
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


    this.optionsLeft = this.createHeatmapOptionsWithoutDataset( this.groupMapping?.a ?? "SetA", true);
    this.optionsRight = this.createHeatmapOptionsWithoutDataset( this.groupMapping?.b ?? "SetB", true);
    this.differenceOptions = this.createDeltaHeatmapOptions("Delta_z", true);

    this.chartInstance1.setOption(this.optionsLeft);
    this.chartInstance2.setOption(this.optionsRight);
    this.differenceInstance.setOption(this.differenceOptions);

    this.setupHoverInteractions();


  }

  resizeHeatmaps(): void{
    requestAnimationFrame(() => {
      if (this.showAB) {
        this.chartInstance1.resize();
        this.chartInstance2.resize();
      }
      if (this.showD) {
        this.differenceInstance.resize();
      }
    });

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
    const colors = this.COLOR_Schemes[this.selectedColorScheme].color;
    console.log("apply" + colors);

    const opt = {
      visualMap: [{
        ...this.BASE_VisualMap,
        inRange: {
          color: colors
        },
        min: this.min_veg_height_a,
        max: this.max_veg_height_a,
        show: this.showVisualMap
      }]
    };

    const opt2 = {
      visualMap: [{
        ...this.BASE_VisualMap,
        inRange: {
          color: colors
        },
        min: this.min_veg_height_a,
        max: this.max_veg_height_a,
        show: this.showVisualMap
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


  toggleZoom(): void {
    this.showZoom = !this.showZoom;
    this.applyZoomVisibility();
  }

  private updateVisualMap(show: boolean, delta_chart: boolean) {
    let option;
    let colors;
    delta_chart ? option = this.BASE_DeltaVirtualMap : option = this.BASE_VisualMap;
    delta_chart ? colors = this.COLOR_Schemes.deltas.color : colors = this.COLOR_Schemes[this.selectedColorScheme].color;

    console.log("updateVisualMap" + colors);
    if (!delta_chart){
      const opt1 = {
        visualMap: [
          {
            ...option,
            inRange: {
              color: colors
            },
            min: this.min_veg_height_a,
            max: this.max_veg_height_a,
            show: show
          }
        ]
      }
      const opt2 = {
        visualMap: [
          {
            ...option,
            inRange: {
              color: colors
            },
            min: this.min_veg_height_b,
            max: this.max_veg_height_b,
            show: show
          }
        ]
      }

      this.chartInstance1?.setOption(opt1, { replaceMerge: ['visualMap'] as any });
      this.chartInstance2?.setOption(opt2, { replaceMerge: ['visualMap'] as any });
      return;
    }

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
    this.differenceInstance?.setOption(opt, { replaceMerge: ['visualMap'] as any });


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
      this.updateVisualMap(this.showVisualMap, false);
    }
    if (this.showD) {
      this.updateVisualMap( this.showVisualMap, true);
    }
  }

  private applyZoomVisibility(): void {
    const show = this.showZoom;

    const opt = {
      dataZoom: [
        { // x slider
          type: 'slider',
          xAxisIndex: 0,
          bottom: 0,
          filterMode: 'none',
          show,
        },
        { // y slider
          type: 'slider',
          yAxisIndex: 0,
          orient: 'vertical',
          right: 0,
          filterMode: 'none',
          show,
        }
      ]
    };

    this.chartInstance1?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
    this.chartInstance2?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
    this.differenceInstance?.setOption(opt, {replaceMerge: ['dataZoom'] as any});
  }

  protected toggleLayout() {
      this.heatmapsStackedLayout = !this.heatmapsStackedLayout;
  }


  private createDeltaHeatmapOptions(title: string, showVisualMap: boolean): EChartsCoreOption {
    const options = this.createHeatmapOptionsWithoutDataset(title, showVisualMap);
    const visMap = {
      visualMap: this.BASE_DeltaVirtualMap
    }
    return {...options, ...visMap};
  }


  private updateHeatmaps(matrix: ChunkedCell[][]) {
    if (!matrix || !Array.isArray(matrix) || matrix.length === 0) {
      console.warn("Received invalid heatmap data:", matrix);
      return;
    }
    this.groupMapping.a = this.data?.group_mapping.a ?? "SetA";
    this.groupMapping.b = this.data?.group_mapping.b ?? "SetB";

    this.rows = matrix.length;
    //TODO check if we need to set this.cols even on max, as cols should all be same length
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
        let valA = cell.veg_height_max_a ?? 0;
        let valB = cell.veg_height_max_b ?? 0;

        if (this.showPercentiles) {
          const pKey = Object.keys(cell).find(
            k => k.startsWith('veg_height_p') && k.endsWith('_a')
          )?.replace('_a', '');
          if (pKey) {
            valA = cell[`${pKey}_a`] ?? valA;
            valB = cell[`${pKey}_b`] ?? valB;
          }
        }
        const outA = cell.out_a ?? 0;
        const outB = cell.out_b ?? 0;
        let delta_z = cell.delta_z ?? 0;
        if (this.showPercentiles) {
          const pKey = Object.keys(cell).find(
            k => k.startsWith('veg_height_p') && k.endsWith('_a')
          )?.replace('_a', '');

          if (pKey && cell[`${pKey}_diff`] != null) {
            delta_z = cell[`${pKey}_diff`];
          }
        }
        seriesDataA.push([x0, y0, x1, y1, valA, outA]);
        seriesDataB.push([x0, y0, x1, y1, valB, outB]);
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
      const outlierCount = api.value(5) ?? 0;
      const sx = Math.min(x0, x1);
      const ex = Math.max(x0, x1);
      const sy = Math.min(y0, y1);
      const ey = Math.max(y0, y1);

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

      // Get the color from the visual map based on value
      const color = api.visual('color');

      // Clip against the visible plot area
      // (Prevents drawing outside the visible range) for dataZoom
      const rectShape = { x, y, width, height };
      const clipped = echarts.graphic.clipRectByRect(rectShape, params.coordSys);

      // If completely outside -> don't draw
      if (!clipped) return null;

      // Build children array with clipped rect
      const children: any[] = [
        {
          type: 'rect',
          shape: clipped,
          style: {
            fill: color,
            stroke: 'none',
            lineWidth: 0,
            borderWidth: 0
          }
        }
      ];

      // Add outlier indicator if outliers exist
      if (this.showOutliers && outlierCount > 0) {
        const dotRadius = Math.max(2, Math.min(clipped.width, clipped.height) * 0.15); // ensure at least 2px
        children.push({
          type: 'circle',
          shape: {
            cx: clipped.x + clipped.width / 2,
            cy: clipped.y + clipped.height / 2,
            r: dotRadius
          },
          style: {
            fill: 'rgba(255, 0, 0, 0.85)',
            stroke: '#fff',
            lineWidth: 0.5
          },
          z: 10
        });
      }

      return {
        type: 'group',
        children: children
      };
    };

    const titleUpdateA ={
      title: {
        top: 0,
        text: this.groupMapping?.a
      }
    }
    const titleUpdateB ={
      title: {
        top: 0,
        text: this.groupMapping?.b
      }
    }

    const seriesTemplate = {
      type: 'custom',
      renderItem: renderItem,
      encode: {
        x: 0,
        y: 1,
      },
      dimensions: ['x0', 'y0', 'x1', 'y1', 'value', 'outlierCount']
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
      // params.data is: [x0, y0, x1, y1, value, outlierCount]
      const d = params?.data;
      if (!d || d.length < 5) return '';
      const x0 = d[0], y0 = d[1], x1 = d[2], y1 = d[3], value = d[4];
      const outlierCount = d[5] ?? 0;
      const sx = Math.min(x0, x1), ex = Math.max(x0, x1);
      const sy = Math.min(y0, y1), ey = Math.max(y0, y1);
      let tooltip = `X: ${sx} - ${ex}<br/>Y: ${sy} - ${ey}<br/>Value: ${typeof value === 'number' ? value.toFixed(3) : value}`;
      if (outlierCount > 0) {
        tooltip += `<br/><span style="color:red">Outliers: ${outlierCount}</span>`;
      }
      return tooltip;
    };
    this.chartInstance1.setOption({
      ...titleUpdateA,
      ...axisUpdate,
      tooltip: {
        trigger: "item",
        formatter: tooltipFormatter,
          },
      visualMap: {
        min: this.min_veg_height_a,
        max: this.max_veg_height_a,
        dimension: 4  // Use dimension 4 (value) for color mapping
      },
      series: [{
        ...seriesTemplate,
        data: seriesDataA
      }]
    });

    // Set options for right chart
    this.chartInstance2.setOption({
      ...titleUpdateB,
      ...axisUpdate,
      tooltip: {
        trigger: "item",
        formatter: tooltipFormatter,
           },
      visualMap: {
        min: this.min_veg_height_b,
        max: this.max_veg_height_b,
        dimension: 4  // Use dimension 4 (value) for color mapping
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
        formatter: tooltipFormatter,
     },
      visualMap: [{
        ...this.BASE_DeltaVirtualMap,
        show: this.showVisualMap
      }],
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
        {type: 'slider', xAxisIndex: 0, bottom: 0, filterMode: 'none', show: this.showZoom},
        {type: 'slider', yAxisIndex: 0, orient: 'vertical', right: 0, filterMode: 'none', show: this.showZoom}
      ],
      visualMap: [{
        min: 0,
        max: 30,
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
      source.on('mouseover', (p: any) => {
        if (!isSeriesItem(p)) return;

        for (const t of targets) {
          t.dispatchAction({
            type: 'showTip',
            seriesIndex: 0,
            dataIndex: p.dataIndex
          });

          t.dispatchAction({
            type: 'highlight',
            seriesIndex:  0,
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
            seriesIndex: 0,
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





  private BASE_VisualMap: any = {
    // min: this.min_veg_height_a,
    // max: this.max_veg_height_a,
    calculable: true,
    orient: 'vertical',
    left: -5,
    top: "middle",
    text: [],   // Beschriftung
    textGap: -5,              // Abstand Text ↔ Farbskala
    inRange: {
      color: this.COLOR_Schemes[this.selectedColorScheme].color //does not change dynamically, has to be set manually
    },
    dimension: 4
  }
  private BASE_DeltaVirtualMap = {
      type: 'continuous',
      min: -10,
      max: 10,
      orient: 'vertical',
      left: -10,
      top: "middle",
      calculable: true,
      dimension: 4,
      inRange: {
        color: this.COLOR_Schemes.deltas.color
      }
    }





}
