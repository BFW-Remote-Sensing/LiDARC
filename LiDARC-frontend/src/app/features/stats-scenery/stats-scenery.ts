import {AfterViewInit, Component, OnInit} from '@angular/core';
import {provideEchartsCore} from 'ngx-echarts';
import * as echarts from 'echarts/core';
import {EChartsCoreOption} from 'echarts/core';
import {CandlestickChart} from 'echarts/charts';
import {FormsModule} from '@angular/forms';
import {LegacyGridContainLabel} from 'echarts/features';
import {DataZoomComponent, TitleComponent} from 'echarts/components'
import {MatSlider, MatSliderThumb} from '@angular/material/slider';
import {ComparisonService} from '../../service/comparison.service';
import {switchMap, takeWhile, catchError, map, tap, filter, debounceTime, retry } from 'rxjs/operators';
import { Subject, Subscription, interval, of, throwError, timer } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

echarts.use([CandlestickChart, TitleComponent, DataZoomComponent, LegacyGridContainLabel]);


@Component({
  selector: 'app-stats-scenery',
  standalone: true,
  imports: [
    FormsModule,
    MatSlider,
    MatSliderThumb,


  ],
  templateUrl: './stats-scenery.html',
  styleUrl: './stats-scenery.scss',
  providers: [
    provideEchartsCore({echarts})
  ]
})

export class StatsScenery implements OnInit, AfterViewInit {

  quadrantsFirstOptions!: EChartsCoreOption;
  quadrantSecondOptions!: EChartsCoreOption;
  sceneryOptions!: EChartsCoreOption;
  p90FirstOptions!: EChartsCoreOption;
  p95FirstOptions!: EChartsCoreOption;
  p90SecondOptions!: EChartsCoreOption;
  p95SecondOptions!: EChartsCoreOption;

  quadrantFirstElement!: HTMLElement | null;
  quadrantSecondElement!: HTMLElement | null;

  sceneryElement!: HTMLElement | null;

  p90FirstElement!: HTMLElement | null;
  p95FirstElement!: HTMLElement | null;

  p90SecondElement!: HTMLElement | null;
  p95SecondElement!: HTMLElement | null;
  groupSize: number = 16;

  comparisonNameTemplate = ["grouped_", "scenery_", "p90_", "p95_"];

  comparisonPerContainer: number = 2;

  mockData = this.getMockGroupSizeData(10);

  xAxisNamings: string[] = this.createXAxisNamings();

  cellsMatrix: string[][] = [];
  chunkedMatrix: string[][] = [];
  private groupSizeChange$ = new Subject<number>();
  private pollingSubscription?: Subscription;

  constructor(
    private comparisonService: ComparisonService,
  ) {}

  ngOnInit(): void {
    this.pollingSubscription = this.groupSizeChange$.pipe(
      debounceTime(300),
      switchMap(size => this.requestVisualizationData(size, 1))
    ).subscribe({
      next: (data) => {
        console.log("Process complete:", data);
        this.updateEChartsOptions();
      },
      error: (err) => console.error("Pipeline failed", err)
    })
    const datapoints = 100;
    const quartileData = this.getMockGroupSizeData(datapoints);
    console.log(quartileData);
    this.createEChartsElements();
  }

  ngAfterViewInit(): void {
    console.log('ngAfterViewInit');
  }


  private getPercentileOptions(data: number[][], title: string, subtitle?: string) {

    return {
      title: {
        top: 0,
        text: title,
        subtext: subtitle
      },
      itemStyle: {
        color: '#888888',
        color0: '#888888',
        borderColor: '#555555',
        borderColor0: '#555555',
      },
      xAxis: {
        data: this.xAxisNamings
      },
      yAxis: {},
      series: [
        {
          type: "candlestick",
          data: data,
        }
      ],
      dataZoom: [
        // Slider unten für X-Achse
        {
          type: 'slider',
          xAxisIndex: 0,
          bottom: 0,
          filterMode: 'none', // Daten nicht rausfiltern, nur Ansicht beschneiden
        },
      ]
    }
  }

  private getQuadrantOptions(data: number[][], title: string, subtitle?: string) {
    return {
      title: {
        top: 0,
        text: title,
        subtext: subtitle
      },
      itemStyle: {
        color: '#888888',
        color0: '#888888',
        borderColor: '#555555',
        borderColor0: '#555555',
      },
      xAxis: {
        data: this.xAxisNamings,
      },
      yAxis: {},
      series: [
        {
          type: "candlestick",
          data: data,
        }
      ],
      dataZoom: [
        // Slider unten für X-Achse
        {
          type: 'slider',
          xAxisIndex: 0,
          bottom: 0,
          filterMode: 'none', // Daten nicht rausfiltern, nur Ansicht beschneiden
        },
      ]
    }
  }

  createXAxisNamings(): string[] {
    const xAxisNamings: string[] = [];
    for (let i = 0; i < this.groupSize; i++) {
      xAxisNamings.push("P" + (i + 1));
    }
    return xAxisNamings;
  }


  private getMockGroupSizeData(datapoints: number): number[][] {
    const result: number[][] = [];
    for (let q = 0; q < this.groupSize; q++) {
      const quartile: number[] = [];

      for (let i = 0; i < datapoints; i++) {
        quartile.push(Math.random() * 30); // Werte 0–30
      }

      result.push(quartile);
    }
    return result;
  }

  protected onGroupSizeChange(newSize: number) {
    console.log(this.groupSize)
    this.groupSizeChange$.next(newSize);
  }

  private requestVisualizationData(chunkSize: number, comparisonId: number) {
    return this.comparisonService.startChunkingResult(comparisonId, chunkSize).pipe(
      tap(() => console.log("Starting chunking worker...")),
      switchMap(() => this.pollVisualizationData(comparisonId)),
      catchError(err => {
        console.log("Error during start or polling:", err);
        return of(null)
      }),
      filter(data => data !== null)
    );
  }

  private pollVisualizationData(comparisonId: number) {
    return timer(0, 1000).pipe(
      switchMap(() => this.comparisonService.pollChunkingResult(comparisonId)),
      map((response: HttpResponse<any>) => {
        if (response.status === 202) return { status: 'PENDING', data: null };
        if (response.status === 200) return { status: 'COMPLETED', data: response.body };
        throw new Error(`Unexpected status: ${response.status}`);
      }),
      takeWhile(res => res.status !== 'COMPLETED', true),
      filter(res => res.status === 'COMPLETED'),
      map(res => res.data)
    );
  }


  private createEChartsElements(data?: string[][], dataName?: string): void {
    const overallEchartsContainer = document.getElementById("echarts-container");
    if (!overallEchartsContainer) return console.log("No overall container found!");

    const groupingContainer = document.getElementById("grouping-container");
    if (!groupingContainer) return console.log("No grouping container found!");
    for (let i = 0; i < overallEchartsContainer.children.length; i++) {
      for (let j = 1; j < this.comparisonPerContainer + 1; j++) {
        let groupingElement = document.getElementById(this.comparisonNameTemplate[i] + j);
        if (!groupingElement) {
          console.log("No grouping element found!");
          continue;
        }
        let echartsElement = echarts.init(groupingElement);
        let opt: EChartsCoreOption = {};
        if (this.comparisonNameTemplate[i] == "grouped_") {
          opt = this.getQuadrantOptions(this.mockData, "100 percentile", dataName);
        } else if (this.comparisonNameTemplate[i] == "scenery_") {
          opt = this.getQuadrantOptions(this.mockData, "scenery analysis", dataName);
        } else if (this.comparisonNameTemplate[i] == "p90_") {
          opt = this.getPercentileOptions(this.mockData, "for 90 Percentile", dataName);
        } else if (this.comparisonNameTemplate[i] == "p95_") {
          opt = this.getPercentileOptions(this.mockData, "for 95 Percentile", dataName);
        } else continue;

        echartsElement.setOption(opt);
      }
    }

  }

  private updateEChartsOptions(): void {
    console.log("Updating ECharts options...");
    const overallEchartsContainer = document.getElementById("echarts-container");
    if (!overallEchartsContainer) return console.log("No overall container found!");

    for (let i = 0; i < overallEchartsContainer.children.length; i++) {
      for (let j = 1; j < this.comparisonPerContainer + 1; j++) {

        let groupingElement = document.getElementById(this.comparisonNameTemplate[i] + j);
        if (!groupingElement) {
          console.log("Updating options: No grouping element found!");
          continue;
        }
        const echartsElement = echarts.getInstanceByDom(groupingElement);
        echartsElement?.getId();
        if (!echartsElement) {
          console.log("Updating options: No echarts grouping element found!");
          continue;
        }
        let title = "";
        if (this.comparisonNameTemplate[i] == "grouped_") {
          title = "grouped into " + this.groupSize + " parts";
        } else if (this.comparisonNameTemplate[i] == "scenery_") {
          title = "whole scenery";
        } else if (this.comparisonNameTemplate[i] == "p90_") {
          title = "for 90 Percentile";
        } else if (this.comparisonNameTemplate[i] == "p95_") {
          title = "for 95 Percentile"
        } else continue;

        let opt: EChartsCoreOption = {
          title: {
            text: title,
          },
          xAxis: {
            data: this.xAxisNamings,
          },
          yAxis: {},
          series: [
            {
              type: "candlestick",
              data: this.mockData,
            }
          ]
        }
        console.log("Setting options...");
        echartsElement.setOption(opt);
      }
    }
  }
}
