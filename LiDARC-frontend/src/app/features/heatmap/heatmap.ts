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
  chartOptions!: EChartsCoreOption;

  ngOnInit() {
    const rows = 50;
    const cols = 50;
    const data1 = this.generateVegetationData(rows, cols);
    const data2 = this.generateVegetationData(rows, cols);
    // Specify the configuration items and data for the chart
    this.chartOptions = {
      tooltip: {
        position: 'top',
        formatter: (params: any) => {
          const [x, y, value] = params.value;
          return `x: ${x}, y: ${y}<br/>Wert: ${value.toFixed(2)}`;
        },
      },
      grid: {
        height: '80%',
        top: '10%',
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
        max: 30,              // Wertebereich (z.B. 0–30 m Vegetationshöhe)
        calculable: true,
        orient: 'vertical',
        left: 'left',
        top: 'center',
      },
      series: [
        {
          name: 'Heatmap',
          type: 'heatmap',
          data1,
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
    setTimeout(() => {
      const chartElement1 = document.getElementById('chart1');
      const chartElement2 = document.getElementById('chart2');
      // 1) DOM-Elemente sicherstellen
      if (!chartElement1 || !chartElement2) {
        console.error('Chart DOM elements not found');
        return;
      }

      // 2) ECharts-Instanzen holen
      const chart1 = getInstanceByDom(chartElement1);
      const chart2 = getInstanceByDom(chartElement2);

      // 3) Instanzen können auch undefined sein → checken
      if (!chart1 || !chart2) {
        console.error('ECharts instances not found on given elements');
        return;
      }
      connect([chart1,chart2]);
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
