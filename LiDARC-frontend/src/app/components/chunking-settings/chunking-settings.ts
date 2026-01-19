import {Component, EventEmitter, Input, OnInit, Output, SimpleChanges} from '@angular/core';
import {MatSlider, MatSliderThumb} from '@angular/material/slider';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {merge, of, Subject, Subscription, timer, BehaviorSubject, Observable} from 'rxjs';
import {ComparisonService} from '../../service/comparison.service';
import {catchError, debounceTime, filter, map, switchMap, takeWhile, tap} from 'rxjs/operators';
import {ChunkedCell, ChunkingResult} from '../../dto/chunking';
import {HttpResponse} from '@angular/common/http';

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

  protected groupSize: number = 2;
  private groupSizeChange$ = new Subject<number>();
  private pollingSubscription?: Subscription;
  @Output() visualizationData = new EventEmitter<ChunkingResult>();

  private _comparisonId?: number;

  @Input()
  set comparisonId(value: number | null | undefined) {
    if (value) {
      this._comparisonId = value;
      console.log("Setting of comparisonId in chunking-settings and starting initial polling of data, ID: ", this._comparisonId)
      // Initial load observable with all cells
      const initialLoad$ = of(this.groupSize);

      this.pollingSubscription = merge(
        initialLoad$,
        this.groupSizeChange$
      ).pipe(
        debounceTime(300),
        switchMap(size => this.requestVisualizationData(size, value))
      ).subscribe({
        next: (data) => {
          console.log("Visualization updated:", data);
          this.visualizationData.emit(data);
        },
        error: (err) => console.error("Polling failed", err)
      });
    }
  }

  get comparisonId(): number | undefined {
    return this._comparisonId;
  }

  constructor(
    private comparisonService: ComparisonService,

  ) {}

  ngOnInit(): void {

  }

  protected onGroupSizeChange(newSize: number) {
    console.log("Grouped into blocks of size:", this.groupSize);
    console.log("new size: ", newSize);
    this.groupSizeChange$.next(newSize);
  }


  private requestVisualizationData(chunkSize: number, comparisonId: number) {
    return this.comparisonService.startChunkingResult(comparisonId, chunkSize).pipe(
      tap(() => console.log("Starting chunking worker...with chunking size" + chunkSize)),
      switchMap(() => this.streamVisualizationData(comparisonId, chunkSize)),
      catchError(err => {
        console.log("Error during start or streaming:", err);
        return of(null)
      }),
      filter(data => data !== null)
    );
  }

  /**
   * Uses SSE streaming to receive chunking results instead of polling.
   * Results are cached per chunkSize.
   */
  private streamVisualizationData(comparisonId: number, chunkSize: number) {
    return this.comparisonService.streamChunkingResult(comparisonId, chunkSize);
  }

  /**
   * Fallback polling method (kept for reference, not currently used).
   */
  private pollVisualizationData(comparisonId: number, chunkSize: number) {
    return timer(0, 1000).pipe(
      switchMap(() => this.comparisonService.pollChunkingResult(comparisonId, chunkSize)),
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


}
