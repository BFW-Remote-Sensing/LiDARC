import {AfterViewInit, Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {NgxEchartsDirective, provideEchartsCore} from 'ngx-echarts';
import {EChartsCoreOption} from 'echarts/core';
import {TooltipComponent, VisualMapComponent} from 'echarts/components';
import {getInstanceByDom, connect} from 'echarts/core';


// import echarts core
import * as echarts from 'echarts/core';
// import necessary echarts components
import {BarChart, HeatmapChart} from 'echarts/charts';
import {GridComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {LegacyGridContainLabel} from 'echarts/features';
import {TitleComponent} from 'echarts/components'
import {DataZoomComponent} from 'echarts/components'
import {share} from 'rxjs';
import {ECharts, EChartsType} from 'echarts';

echarts.use([TitleComponent, DataZoomComponent, LegacyGridContainLabel, TooltipComponent, VisualMapComponent, BarChart, GridComponent, CanvasRenderer, HeatmapChart]);


@Component({
  selector: 'app-heatmap',
  standalone: true,
  imports: [
     CommonModule
  ],
  templateUrl: './heatmap.html',
  styleUrl: './heatmap.scss',
  providers: [
    provideEchartsCore({echarts})
  ]
})

export class Heatmap implements OnInit, AfterViewInit {
  optionsLeft!: EChartsCoreOption;
  optionsRight!: EChartsCoreOption;
  optionsSeries!: EChartsCoreOption;

  chartElement1!: HTMLElement | null;
  chartElement2!: HTMLElement | null;

  rows = 100;
  cols = 100;


  ngOnInit(): void {
    //later we will fetch real data and insert it into the options
    const dataLeft = this.generateVegetationData(this.rows, this.cols);
    const dataRight = this.generateVegetationData(this.rows, this.cols);

    this.chartElement1 = document.getElementById('chart1');
    this.chartElement2 = document.getElementById('chart2');

    if (this.chartElement1 === null || this.chartElement2 === null) {
      alert("chart not found!");
    }

    this.optionsLeft = this.createHeatmapOptionsWithoutDataset(dataLeft,"SetA", true);
    this.optionsRight = this.createHeatmapOptionsWithoutDataset( dataRight,"SetB", false);


    const chart1 = echarts.init(this.chartElement1);
    chart1.setOption(this.optionsLeft);
    //chart1.showLoading();

    const chart2 = echarts.init(this.chartElement2);
    chart2.setOption(this.optionsRight);

    this.setHighlightBorderOnMouseover(chart1, chart2);
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
          const [x, y, value] = params.value;
          return `x: ${x}, y: ${y}<br/>Höhe: ${value.toFixed(2)} m`;
        },
        grid: {
          height: '75%',
          top: 70,
        },
      },xAxis: {
         type: 'category',
         data: Array.from({length: this.cols}, (_, i) => i.toString()),
         splitArea: {show: false},
       },
       yAxis: {
         type: 'category',
         data: Array.from({length: this.rows}, (_, i) => i.toString()),
         splitArea: {show: false},
       },
       dataZoom: [
         // Slider unten für X-Achse
         {
           type: 'slider',
           xAxisIndex: 0,
           bottom: 0,
           filterMode: 'none', // Daten nicht rausfiltern, nur Ansicht beschneiden
         },
         // Slider rechts für Y-Achse
         {
           type: 'slider',
           yAxisIndex: 0,
           orient: 'vertical',
           right: 0,
           filterMode: 'none',
         },
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
      }, series: [
         {
           type: 'heatmap',
           data,
           emphasis: {
             itemStyle: {
               borderColor: '#ff000',
               borderWidth: 2,
             },
           },
         },
       ]
    };
  }

  ngAfterViewInit(): void {
    this.connectHeatmaps();
  }

  private checkIfLoadingNecessary(): void {

  }

  private connectHeatmaps() {
    setTimeout(() => {

      //ensure dom is present
      if (!this.chartElement1 || !this.chartElement2) {
        console.error('Chart DOM elements not found');
        return;
      }


      const chart1 = getInstanceByDom(this.chartElement1);
      const chart2 = getInstanceByDom(this.chartElement2);

      //check for null values
      if (!chart1 || !chart2) {
        console.error('ECharts instances not found on given elements');
        return;
      }

      //make interactive connected charts
      connect([chart1, chart2]);
    });
  }

  private generateVegetationData(rows: number, cols: number):
    number[][] {
    const result: number[][] = [];
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const value = Math.random() * 30;
        result.push([x, y, value]);
      }
    }
    return result;
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
