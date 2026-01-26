import {Component, EventEmitter, Input, OnInit, Output, SimpleChanges} from '@angular/core';
import {MatSlider, MatSliderThumb} from '@angular/material/slider';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {
  merge, of, Subject, Subscription, timer, BehaviorSubject, Observable, startWith, combineLatest,
  distinctUntilChanged
} from 'rxjs';
import {ComparisonService} from '../../service/comparison.service';
import {catchError, debounceTime, filter, map, switchMap, takeWhile, tap} from 'rxjs/operators';
import {ChunkedCell, ChunkingResult} from '../../dto/chunking';
import {HttpResponse} from '@angular/common/http';
import {ComparisonDTO} from '../../dto/comparison';

@Component({
  selector: 'app-chunking-settings',
  imports: [
    MatSlider,
    MatSliderThumb,
    ReactiveFormsModule,
    FormsModule
  ],
  templateUrl: './chunking-settings.html',
  styleUrl: './chunking-settings.scss',
})

export class ChunkingSettings implements OnInit{


  private _comparisonId?: number;
  protected _groupSize: number = 5;
  //@Input() groupSize: number = 3;
  //private groupSizeChange$ = new Subject<number>();
  private comparisonId$ = new BehaviorSubject<number | null>(null);
  private groupSize$ = new BehaviorSubject<number>(this._groupSize);
  private pollingSubscription?: Subscription;
  @Output() visualizationData = new EventEmitter<ChunkingResult>();


  //
  // @Input()
  // set comparisonId(value: number | null | undefined) {
  //   if (value) {
  //     this._comparisonId = value;
  //     console.log("Setting of comparisonId in chunking-settings and starting initial polling of data, ID: ", this._comparisonId)
  //     // Initial load observable with all cells
  //     const initialLoad$ = of(this.groupSize);
  //
  //     this.pollingSubscription = merge(
  //       initialLoad$,
  //       this.groupSizeChange$
  //     ).pipe(
  //       debounceTime(300),
  //       switchMap(size => this.requestVisualizationData(size, value))
  //     ).subscribe({
  //       next: (data) => {
  //         console.log("Visualization updated:", data);
  //         this.visualizationData.emit(data);
  //       },
  //       error: (err) => console.error("Polling failed", err)
  //     });
  //   }
  // }

  @Input()
  set comparisonId(value: number | null | undefined) {
    this.comparisonId$.next(value ?? null);
  }

  @Input()
  set groupSizeInput(value: number | null | undefined) {
    console.log("Setting group size in chunking-settings:", value);
    const v = value ?? 5;
    this._groupSize = v;
    this.groupSize$.next(v);
  }


  constructor(
    private comparisonService: ComparisonService,

  ) {}

  ngOnInit(): void {
    //combine latest emits when both emitted at least once
      this.pollingSubscription = combineLatest([
        this.comparisonId$,
        this.groupSize$
      ]).pipe(
        filter(([id]) => id != null),
        debounceTime(300), // optional: auf das Paar anwenden
        distinctUntilChanged(([id1, s1], [id2, s2]) => id1 === id2 && s1 === s2),
        switchMap(([id, size]) => this.requestVisualizationData(size, id!))
      ).subscribe({
        next: (data) => this.visualizationData.emit(data),
        error: (err) => console.error("Polling failed", err)
      });


  }

  protected onGroupSizeChange(newSize: number) {
    console.log("Grouped into blocks of size:", this._groupSize);
    console.log("new size: ", newSize);
    this._groupSize = newSize;
    this.groupSize$.next(newSize);
  }


  private requestVisualizationData(chunkSize: number, comparisonId: number) {
    return this.comparisonService.startChunkingResult(comparisonId, chunkSize).pipe(
      tap(() => console.log("Starting chunking worker...with chunking size" + chunkSize)),
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

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }


}
