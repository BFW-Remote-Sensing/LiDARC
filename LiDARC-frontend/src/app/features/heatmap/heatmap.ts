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

echarts.use([LegacyGridContainLabel, TooltipComponent, VisualMapComponent, BarChart, GridComponent, CanvasRenderer, HeatmapChart]);


@Component({
  selector: 'app-heatmap',
  standalone: true,
  imports: [
    NgxEchartsDirective, CommonModule
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

  rows = 400;
  cols = 400;

  ngOnInit(): void {
    //later we will fetch real data and insert it into the options
    const dataLeft = this.generateVegetationData(this.rows, this.cols);
    const dataRight = this.generateVegetationData(this.rows, this.cols);

    this.optionsLeft = this.createHeatmapOptions(dataLeft, 'Set A');
    this.optionsRight = this.createHeatmapOptions(dataRight, 'Set B');
  }


  private createHeatmapOptions(data: number[][], title: string): EChartsCoreOption {
    const rows = this.rows;
    const cols = this.cols;


    return {
      title: {
        text: title,
        left: 'center',
      },
      tooltip: {
        position: 'top',
        formatter: (params: any) => {
          const [x, y, value] = params.value;
          return `x: ${x}, y: ${y}<br/>Höhe: ${value.toFixed(2)} m`;
        },
      },
      grid: {
        height: '75%',
        top: 40,
      },
      xAxis: {
        type: 'category',
        data: Array.from({length: cols}, (_, i) => i.toString()),
        splitArea: {show: false},
      },
      yAxis: {
        type: 'category',
        data: Array.from({length: rows}, (_, i) => i.toString()),
        splitArea: {show: false},
      },
      visualMap: {
        min: 0,
        max: 30,
        calculable: true,
        orient: 'vertical',
        left: 0,
        top: 'middle',
        inRange: {
          color: ['#e5f5e0', '#a6dba0', '#5aae61', '#1b7837', '#00441b'],
        }
      },
      series: [
        {
          type: 'heatmap',
          data,
          emphasis: {
            itemStyle: {
              borderColor: '#333',
              borderWidth: 1,
            },
          },
        },
      ],
    };
  }

  ngAfterViewInit(): void {
    this.connectHeatmaps();
  }

  private connectHeatmaps() {
    setTimeout(() => {
      const chartElement1 = document.getElementById('chart1');
      const chartElement2 = document.getElementById('chart2');
      //ensure dom is present
      if (!chartElement1 || !chartElement2) {
        console.error('Chart DOM elements not found');
        return;
      }


      const chart1 = getInstanceByDom(chartElement1);
      const chart2 = getInstanceByDom(chartElement2);

      //check for null values
      if (!chart1 || !chart2) {
        console.error('ECharts instances not found on given elements');
        return;
      }

      //make interactive connected charts
      connect([chart1, chart2]);
    });
  }

  private generateVegetationData(rows: number, cols: number): number[][] {
    const result: number[][] = [];
    for (let y = 0; y < rows; y++) {
      for (let x = 0; x < cols; x++) {
        const value = Math.random() * 30;
        result.push([x, y, value]);
      }
    }
    return result;
  }

}
