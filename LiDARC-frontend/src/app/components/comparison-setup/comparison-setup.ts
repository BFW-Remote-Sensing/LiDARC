import { ChangeDetectorRef, Component, Input, signal, WritableSignal } from '@angular/core';
import { SelectedItemService } from '../../service/selectedItem.service';
import { MatAnchor, MatButtonModule } from "@angular/material/button";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDivider } from '@angular/material/divider';
import { MatCheckbox } from '@angular/material/checkbox';
import { CreateComparison } from '../../dto/comparison';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ComparisonService } from '../../service/comparison.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { GridDefinitionDialogComponent } from '../define-grid/define-grid';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from "@angular/material/icon";
import { ConfirmationDialogComponent, ConfirmationDialogData } from '../confirmation-dialog/confirmation-dialog';
import { ReferenceFileService } from '../../service/referenceFile.service';
import { ComparableListItem } from '../../dto/comparableItem';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { FormatBytesPipe } from '../../pipes/formatBytesPipe';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FileMetadataDTO } from '../../dto/fileMetadata';
import { FolderFilesDTO } from '../../dto/folderFiles';
import { getExtremeValue } from '../../helpers/extremeValue';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { finalize, Observable } from 'rxjs';

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
    MatDivider,
    MatCheckbox,
    RouterModule,
    MatProgressSpinner,
    MatCardModule,
    MatIcon,
    MatTooltipModule,
    DragDropModule,
    FormatBytesPipe
  ],
  templateUrl: './comparison-setup.html',
  styleUrls: ['./comparison-setup.scss', '../comparable-items/comparable-items.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({ height: '0px', minHeight: '0', visibility: 'hidden' })),
      state('expanded', style({ height: '*', visibility: 'visible' })),
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
    grid: null
  };
  public loadingStart: WritableSignal<boolean> = signal(false);
  public errorMessage: WritableSignal<string | null> = signal(null);

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
  ) { }

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
      queryParams: { expandedItemId: this.isExpanded(element) ? element.id : null },
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
    if (this.refService.selectedComparableItem() == null) {
      return;
    }
    const item = this.refService.selectedComparableItem()!;

    const dialogRef = this.dialog.open(GridDefinitionDialogComponent, {
      width: '600px',
      height: 'auto',
      data: {
        item: {
          name: item.name,
          minX: item.type === 'File' ?
            (item as FileMetadataDTO).minX :
            getExtremeValue((item as FolderFilesDTO).files, f => f.minX, 'min'),
          maxX: item.type === 'File' ?
            (item as FileMetadataDTO).maxX :
            getExtremeValue((item as FolderFilesDTO).files, f => f.maxX, 'max'),
          minY: item.type === 'File' ?
            (item as FileMetadataDTO).minY :
            getExtremeValue((item as FolderFilesDTO).files, f => f.minY, 'min'),
          maxY: item.type === 'File' ?
            (item as FileMetadataDTO).maxY :
            getExtremeValue((item as FolderFilesDTO).files, f => f.maxY, 'max')
        }
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.comparison = {
          ...this.comparison,
          grid: {
            cellWidth: Number(result.cellWidth.toFixed(2)),
            cellHeight: Number(result.cellHeight.toFixed(2)),
            xMin: Number(result.xMin.toFixed(3)),
            xMax: Number(result.xMax.toFixed(3)),
            yMin: Number(result.yMin.toFixed(3)),
            yMax: Number(result.yMax.toFixed(3))
          }
        };
        this.cdr.markForCheck();
      }
    });
  }

  dropFile(event: CdkDragDrop<any[]>, folder: any) {
    moveItemInArray(folder.files, event.previousIndex, event.currentIndex);

    folder.files = [...folder.files];
  }

  startComparison(): Observable<any> {
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

  ngOnDestroy(): void {
    this.refService.clearSelectedComparableItem();
  }
}
