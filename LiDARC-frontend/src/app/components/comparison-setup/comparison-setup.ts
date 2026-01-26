import {ChangeDetectorRef, Component, Input, signal, WritableSignal} from '@angular/core';
import {SelectedItemService} from '../../service/selectedItem.service';
import {MatAnchor, MatButtonModule} from "@angular/material/button";
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatDivider, MatDividerModule} from '@angular/material/divider';
import {MatCheckbox} from '@angular/material/checkbox';
import {CreateComparison} from '../../dto/comparison';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {ComparisonService} from '../../service/comparison.service';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {MatDialog} from '@angular/material/dialog';
import {DefineGrid} from '../define-grid/define-grid';
import {MatCardModule} from '@angular/material/card';
import {MatIcon} from "@angular/material/icon";
import {ConfirmationDialogComponent, ConfirmationDialogData} from '../confirmation-dialog/confirmation-dialog';
import {
  PointFilterDialogData,
  PointFilterDialogue,
  PointFilterResult
} from '../point-filter-dialogue/point-filter-dialogue';
import {ReferenceFileService} from '../../service/referenceFile.service';
import {ComparableListItem} from '../../dto/comparableItem';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {MatSelectModule} from '@angular/material/select';
import {CommonModule} from '@angular/common';
import {FormatBytesPipe} from '../../pipes/formatBytesPipe';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {MatTooltipModule} from '@angular/material/tooltip';
import * as L from 'leaflet';
import {FileMetadataDTO} from '../../dto/fileMetadata';
import {FolderFilesDTO} from '../../dto/folderFiles';
import {getExtremeValue} from '../../helpers/extremeValue';
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';
import {CoordinateService} from '../../service/coordinate.service';
import {finalize, Observable } from 'rxjs';import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';

@Component({
  selector: 'app-comparison-setup',
  imports: [
    MatAnchor,
    MatButtonModule,
    MatTableModule,
    MatSelectModule,
    FormsModule,
    CommonModule,
    MatInputModule,
    MatFormFieldModule,
    MatDividerModule,
    MatCheckbox,
    RouterModule,
    MatProgressSpinner,
    MatCardModule,
    MatIcon,
    MatTooltipModule,
    DragDropModule,
    MatSnackBarModule,
    FormatBytesPipe
  ],
  templateUrl: './comparison-setup.html',
  styleUrls: ['./comparison-setup.scss', '../comparable-items/comparable-items.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0', visibility: 'hidden'})),
      state('expanded', style({height: '*', visibility: 'visible'})),
      transition('expanded <=> collapsed', animate('200ms ease')),
    ]),
  ],
})
export class ComparisonSetup {
  @Input() comparison: CreateComparison = {
    name: '',
    needHighestVegetation: false,
    needOutlierDetection: false,
    needStatisticsOverScenery: false,
    needMostDifferences: false,
    folderAFiles: [],
    folderBFiles: [],
    grid: null,
    pointFilterLowerBound: null,
    pointFilterUpperBound: null,
    needPointFilter: false,
    outlierDeviationFactor: 2.0
  };
  public loadingStart: WritableSignal<boolean> = signal(false);
  public errorMessage: WritableSignal<string | null> = signal(null);
  private resultMap: L.Map | undefined;
  public gridDefined = false;
  public currentZone: number | null = null;
  displayedColumns: string[] = ['open', 'select', 'name', 'type', 'status',
    'captureYear', 'sizeBytes',
    'uploadedAt', 'actions'];
  innerTableColumns: string[] = ['index', 'filename', 'sizeBytes', 'status', 'captureYear', 'uploadedAt', 'actions'];
  displayedColumnsWithExpand = [...this.displayedColumns, 'expand'];
  expandedElement: ComparableListItem | null = null;
  dataSource = new MatTableDataSource<ComparableListItem>([]);
  needExpandCol: boolean = false;

  constructor(
    private selectedItemService: SelectedItemService,
    private comparisonService: ComparisonService,
    private router: Router,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    public refService: ReferenceFileService,
    private route: ActivatedRoute,
    private coordService: CoordinateService,
    private snackBar: MatSnackBar
  ) {
  }

  /** Checks whether an element is expanded. */
  isExpanded(element: ComparableListItem): boolean {
    return this.expandedElement === element;
  }

  /** Toggles the expanded state of an element. */
  toggle(element: ComparableListItem): void {
    if (element.type === 'File') {
      return;
    }
    this.expandedElement = this.isExpanded(element) ? null : element;
    this.router.navigate([], {
      queryParams: {expandedItemId: this.isExpanded(element) ? element.id : null},
      queryParamsHandling: 'merge',
    });
  }

  private needExpandColumn(): boolean {
    const needed = this.dataSource.data.some(item => item.type !== 'File');
    if (!needed) {
      this.displayedColumns = this.displayedColumns.filter(col => col !== 'open');
      if (this.expandedElement) {
        this.expandedElement = null;
      }
    }
    return needed;
  }


  startComparisonDisabled(): boolean {
    return this.comparison.name.trim() === '' ||
      this.comparison.grid === null ||
      (
        this.comparison.needHighestVegetation === false &&
        this.comparison.needOutlierDetection === false &&
        this.comparison.needStatisticsOverScenery === false &&
        this.comparison.needPointFilter === false &&
        this.comparison.needMostDifferences === false
      );
  }

  ngOnInit(): void {
    if (this.selectedItemService.items.size >= 2) {
      this.dataSource.data = Array.from(this.selectedItemService.items);
    }
    this.needExpandCol = this.needExpandColumn();
    this.route.queryParamMap.subscribe(params => {
      const expandedItemId = params.get('expandedItemId');

      if (!expandedItemId) {
        this.expandedElement = null;
        return;
      }

      const element = this.dataSource.data.find(
        item => item.id.toString() === expandedItemId
      );

      if (element && element.type !== 'File') {
        this.expandedElement = element;
      }
    });
  }

  defineGrid(): void {
    const selectedItem = this.refService.selectedComparableItem();
    if (!selectedItem) {
      return;
    }

    // 1. Helper to extract Grid Data (EPSG + Bounds) from an item
    const extractGridData = (obj: any) => {
      // Logic to get EPSG
      return {
        name: obj.name,
        epsg: this.getEpsgCode(obj),
        minX: obj.type === 'File' ?
          (obj as FileMetadataDTO).minX :
          getExtremeValue((obj as FolderFilesDTO).files, f => f.minX, 'min'),
        maxX: obj.type === 'File' ?
          (obj as FileMetadataDTO).maxX :
          getExtremeValue((obj as FolderFilesDTO).files, f => f.maxX, 'max'),
        minY: obj.type === 'File' ?
          (obj as FileMetadataDTO).minY :
          getExtremeValue((obj as FolderFilesDTO).files, f => f.minY, 'min'),
        maxY: obj.type === 'File' ?
          (obj as FileMetadataDTO).maxY :
          getExtremeValue((obj as FolderFilesDTO).files, f => f.maxY, 'max')
      };
    };

    // 2. Prepare data for the selected (Reference) item
    const referenceData = extractGridData(selectedItem);

    // 3. Find and prepare data for the other (Non-selected) item
    const otherItemRaw = this.dataSource.data.find(i => i.id !== selectedItem.id);
    const otherItemData = otherItemRaw ? extractGridData(otherItemRaw) : null;

    // 4. Open Dialog with both items
    const dialogRef = this.dialog.open(DefineGrid, {
      width: '90vw',
      maxWidth: '95vw',
      height: '90vh',
      maxHeight: '95vh',
      data: {
        item: referenceData,      // The selected reference file
        otherItem: otherItemData  // The file that was NOT selected
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.comparison = {
          ...this.comparison,
          grid: {
            cellWidth: Number(result.cellX.toFixed(2)),
            cellHeight: Number(result.cellY.toFixed(2)),
            xMin: Number(result.xMin.toFixed(2)),
            xMax: Number(result.xMax.toFixed(2)),
            yMin: Number(result.yMin.toFixed(2)),
            yMax: Number(result.yMax.toFixed(2)),
          }
        };
        this.currentZone = result.zone;
        this.gridDefined = true;
        this.cdr.detectChanges();
        this.initResultMap(result, referenceData, otherItemData);
      }
    });
  }

  private getEpsgCode(target: any): number | null {
    // Determine the object that holds the metadata (the target itself or the first file in a folder)
    let sourceItem = target;
    if ((!sourceItem.coordinateSystem && !sourceItem.systemIdentifier) && target.files && target.files.length > 0) {
      sourceItem = target.files[0];
    }

    // 1. Try to get code from the new 'coordinateSystem' field (Format: "AUTHORITY:CODE")
    if (sourceItem.coordinateSystem) {
      const parts = sourceItem.coordinateSystem.split(':');
      if (parts.length === 2) {
        const code = parseInt(parts[1], 10);
        return isNaN(code) ? null : code;
      }
    }

    // 2. Fallback to old 'systemIdentifier' logic
    const ident = sourceItem.systemIdentifier;
    if (!ident || ident.toUpperCase() === 'OTHER') {
      return null;
    }

    const codePart = ident.split(';')[0];
    const parsed = parseInt(codePart, 10);
    return isNaN(parsed) ? null : parsed;
  };

  dropFile(event: CdkDragDrop<any[]>, folder: any) {
    moveItemInArray(folder.files, event.previousIndex, event.currentIndex);

    folder.files = [...folder.files];
  }

  startComparison(): Observable<any> {
    // Validate and auto-correct outlier detection
    if (this.comparison.needOutlierDetection) {
      if (!this.comparison.outlierDeviationFactor || this.comparison.outlierDeviationFactor <= 0) {
        console.warn('Outlier detection enabled but no valid sigma factor configured. Disabling outlier detection.');
        this.comparison.needOutlierDetection = false;
      }
    }

    // Validate and auto-correct point filter
    if (this.comparison.needPointFilter) {
      if (this.comparison.pointFilterLowerBound === null || this.comparison.pointFilterUpperBound === null) {
        console.warn('Point filter enabled but no bounds configured. Disabling point filter.');
        this.comparison.needPointFilter = false;
      }
    }
    this.loadingStart.set(true);
    const firstItem = Array.from(this.selectedItemService.items)[0];
    const secondItem = Array.from(this.selectedItemService.items)[1];
    this.comparison.folderAFiles =
      firstItem.type === "Folder" ?
        (firstItem as FolderFilesDTO).files.map(f => f.id) :
        [firstItem.id];
    this.comparison.folderBFiles =
      secondItem.type === "Folder" ?
        (secondItem as FolderFilesDTO).files.map(f => f.id) :
        [secondItem.id];
    this.comparison.folderAId =
      firstItem.type === "Folder" ?
        (firstItem as FolderFilesDTO).id :
        undefined;
    this.comparison.folderBId =
      secondItem.type === "Folder" ?
        (secondItem as FolderFilesDTO).id :
        undefined;
    return this.comparisonService.postComparison(this.comparison);
  }

  openConfirmationDialog(): void {
    const items = this.selectedItemService.items;
    if (!items || items.size === 0) {
      console.warn('No items selected.');
      return;
    }
    const hasOverlap = this.checkSpatialOverlap(items);
    if (!hasOverlap) {
      this.snackBar.open('The Files do not overlap, comparison not possible', 'CLOSE', {
        duration: 3000,
        panelClass: 'error-snackbar'
      })
      return;
    }

    const data: ConfirmationDialogData = {
      title: 'Confirmation',
      subtitle: 'Are you sure you want to start this comparison?',
      primaryButtonText: 'Start',
      secondaryButtonText: 'Cancel',
      onConfirm: () => this.startComparison(),
      successActionText: 'Comparison creation'
    };

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data,
      disableClose: true,
      autoFocus: false
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.selectedItemService.items.clear();
        this.router.navigate(['/comparisons']);
      }
    });
  }

  openPointFilterDialog(): void {
    const data: PointFilterDialogData = {
      title: 'Configure Point Filter',
      subtitle: 'Specify lower and upper bounds (0.00-100.00) to filter points by percentile. Everything under lower bound and over upper bound will be excluded from calculations.',
      primaryButtonText: 'Apply',
      secondaryButtonText: 'Cancel',
      initialLowerBound: this.comparison.pointFilterLowerBound,
      initialUpperBound: this.comparison.pointFilterUpperBound
    };

    const dialogRef = this.dialog.open(PointFilterDialogue, {
      width: '500px',
      data,
      disableClose: true
    });

    dialogRef.afterClosed().subscribe((result: PointFilterResult | { cleared: boolean } | null | undefined) => {
      if (result !== null && result !== undefined) {
        if ('cleared' in result && result.cleared) {
          this.comparison.pointFilterLowerBound = null;
          this.comparison.pointFilterUpperBound = null;
        } else if ('lowerBound' in result && 'upperBound' in result) {
          this.comparison.pointFilterLowerBound = result.lowerBound;
          this.comparison.pointFilterUpperBound = result.upperBound;
          this.comparison.needPointFilter = true;
        }
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.refService.clearSelectedComparableItem();
  }

  private initResultMap(data: any, refItem: any, otherItem: any): void {
    if (this.resultMap) {
      this.resultMap.remove()
    }
    const sw = this.coordService.toLatLng(data.xMin, data.yMin, data.zone);
    const ne = this.coordService.toLatLng(data.xMax, data.yMax, data.zone);
    const bounds = L.latLngBounds(sw, ne);

    const swMax = this.coordService.toLatLng(data.minXMin, data.minYMin, data.zone);
    const neMax = this.coordService.toLatLng(data.maxXMax, data.maxYMax, data.zone);
    let completeBounds = L.latLngBounds(swMax, neMax)

    this.resultMap = L.map('result-map', {
      zoomControl: false,
      scrollWheelZoom: false,
      doubleClickZoom: false,
      boxZoom: false,
      dragging: false,
      keyboard: false,
      attributionControl: false
    });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      opacity: 0.7
    }).addTo(this.resultMap)
    const userBounds = L.latLngBounds(sw, ne);
    const refZone = refItem.epsg || data.zone;
    const refSw = this.coordService.toLatLng(refItem.minX, refItem.minY, refZone);
    const refNe = this.coordService.toLatLng(refItem.maxX, refItem.maxY, refZone);

    if (refItem) {
      L.rectangle(L.latLngBounds(refSw, refNe), {
        color: '#757575',
        weight: 2,
        dashArray: '5, 10',
        fill: false,
        interactive: false
      }).addTo(this.resultMap);
    }
    if (otherItem) {
      const otherZone = otherItem.epsg || 31256; // Fallback matches dialog logic
      const otherSw = this.coordService.toLatLng(otherItem.minX, otherItem.minY, otherZone);
      const otherNe = this.coordService.toLatLng(otherItem.maxX, otherItem.maxY, otherZone);
      if (data.zone !== otherZone) {
        completeBounds = userBounds;
      }
      L.rectangle(L.latLngBounds(otherSw, otherNe), {
        color: '#d30000',
        weight: 2,
        dashArray: '5, 10',
        fill: false,
        interactive: false
      }).addTo(this.resultMap);
    }
    L.rectangle(userBounds, {
      color: '#3f51b5',
      weight: 3,
      fill: true,
      fillOpacity: 0.2,
      interactive: false
    }).addTo(this.resultMap);
    this.resultMap.fitBounds(completeBounds, {padding: [20, 20]});
  }

  private checkSpatialOverlap(items: Set<ComparableListItem>): boolean {
    const itemsArray = Array.from(items);
    if (itemsArray.length === 0) return false;
    let currentBounds = this.getItemBoundsAsTuple(itemsArray[0]);
    if (!currentBounds) return false;
    for (let i = 1; i < itemsArray.length; i++) {
      const nextItemBounds = this.getItemBoundsAsTuple(itemsArray[i]);

      if (!nextItemBounds) return false;

      const result = this.coordService.getLatLngOverlap(
        currentBounds.sw,
        currentBounds.ne,
        nextItemBounds.sw,
        nextItemBounds.ne
      );

      if (!result) {
        return false;
      }
      currentBounds = {
        sw: [result.sw.lat, result.sw.lng],
        ne: [result.ne.lat, result.ne.lng]
      };
    }
    return true;
  }

  private getItemBoundsAsTuple(item: ComparableListItem): { sw: [number, number], ne: [number, number] } | null {
    let minX: number | null = null;
    let minY: number | null = null;
    let maxX: number | null = null;
    let maxY: number | null = null;
    if ('files' in item && Array.isArray(item.files)) {
      if (item.files.length === 0) return null;

      minX = getExtremeValue(item?.files, (f: FileMetadataDTO) => f.minX, 'min');
      minY = getExtremeValue(item?.files, (f: FileMetadataDTO) => f.minY, 'min');
      maxX = getExtremeValue(item?.files, (f: FileMetadataDTO) => f.maxX, 'max');
      maxY = getExtremeValue(item?.files, (f: FileMetadataDTO) => f.maxY, 'max');
    } else {
      const f = item as FileMetadataDTO;
      minX = f.minX;
      minY = f.minY;
      maxX = f.maxX;
      maxY = f.maxY;
    }
    if (minX === null || minY === null || maxX === null || maxY === null) {
      return null;
    }
    const zone = this.getEpsgCode(item) || 31256;
    const sw = this.coordService.toLatLng(minX, minY, zone);
    const ne = this.coordService.toLatLng(maxX, maxY, zone);
    return {sw, ne};
  }
}
