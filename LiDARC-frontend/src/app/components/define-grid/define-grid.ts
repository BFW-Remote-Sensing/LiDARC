import {Component, Inject, OnInit, AfterViewInit, inject} from '@angular/core';
import {MatDialogRef, MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatDialogModule} from '@angular/material/dialog';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatButtonModule} from '@angular/material/button';
import {MatSelectModule} from '@angular/material/select';
import {MatIcon} from "@angular/material/icon";
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatTooltipModule} from '@angular/material/tooltip';
import * as L from 'leaflet';
import {CoordinateService} from '../../service/coordinate.service';
import {CommonModule} from '@angular/common';

export type AOIItem = {
  name: string;
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
  epsg?: number;
}

@Component({
  selector: 'app-grid-definition-dialog',
  templateUrl: 'define-grid.html',
  styleUrl: 'define-grid.scss',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatDialogModule,
    MatSelectModule,
    MatIcon,
    MatSnackBarModule,
    MatTooltipModule
  ]
})

export class DefineGrid implements OnInit, AfterViewInit {

  private map!: L.Map;
  private referenceRect!: L.Rectangle; // The static original AOI boundaries
  private otherRect!: L.Rectangle;     // The boundary of the non-selected item
  private userRect!: L.Rectangle;      // The dynamic new grid boundaries
  private coordService = inject(CoordinateService);

  cellX: number = 1;
  cellY: number = 1;

  xMin: number | null = null;
  xMax: number | null = null;
  yMin: number | null = null;
  yMax: number | null = null;
  maxXMax: number = 0;
  maxYMax: number = 0;
  minXMin: number = 1000000;
  minYMin: number = 1000000;
  selectedItem: AOIItem;
  otherItem: AOIItem; // Store the non-selected item
  currentZone: number;

  constructor(
    public dialogRef: MatDialogRef<DefineGrid>,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { item: AOIItem, otherItem: AOIItem }
  ) {
    this.selectedItem = this.data.item;
    this.otherItem = this.data.otherItem;
    this.currentZone = this.selectedItem.epsg || 31256;

    this.xMin = this.roundMin(this.selectedItem.minX);
    this.xMax = this.roundMax(this.selectedItem.maxX);
    this.yMin = this.roundMin(this.selectedItem.minY);
    this.yMax = this.roundMax(this.selectedItem.maxY);
    this.maxXMax = Math.max(this.xMax, this.otherItem.maxX);
    this.maxYMax = Math.max(this.yMax, this.otherItem.maxY);
    this.minXMin = Math.min(this.xMin, this.otherItem.minX);
    this.minYMin = Math.min(this.yMin, this.otherItem.minY);
  }

  ngOnInit() {
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  async onDefine(): Promise<void> {
    if (this.currentZone && this.cellX != null && this.cellY != null &&
      this.xMin !== null && this.xMax !== null &&
      this.yMin !== null && this.yMax !== null) {
      this.dialogRef.close({
        cellX: this.cellX,
        cellY: this.cellY,
        xMin: this.xMin,
        xMax: this.xMax,
        yMin: this.yMin,
        yMax: this.yMax,
        zone: this.currentZone,
        maxXMax: this.maxXMax,
        minXMin: this.minXMin,
        minYMin: this.minYMin,
        maxYMax: this.maxYMax,
      });
    }
  }

  //TODO: Check why not completely matching?
  resetAoi(): void {
    this.xMin = this.roundMin(this.selectedItem.minX);
    this.xMax = this.roundMax(this.selectedItem.maxX);
    this.yMin = this.roundMin(this.selectedItem.minY);
    this.yMax = this.roundMax(this.selectedItem.maxY);
    this.updatePreview()
  }

  automatedAOIMatch(): void {
    if (this.otherItem === null) return;
    const otherZone = this.otherItem.epsg || 31256;
    const item = this.selectedItem;

    const selSW = this.coordService.toLatLng(item.minX, item.minY, this.currentZone);
    const selNE = this.coordService.toLatLng(item.maxX, item.maxY, this.currentZone);

    const othSW = this.coordService.toLatLng(this.otherItem.minX, this.otherItem.minY, otherZone);
    const othNE = this.coordService.toLatLng(this.otherItem.maxX, this.otherItem.maxY, otherZone);
    const overlap = this.coordService.getLatLngOverlap(selSW, selNE, othSW, othNE);

    if (!overlap) {
      console.log("NO OVERLAP FOUND");
      this.snackBar.open('The selected areas do not overlap anywhere.', 'Close', {
        duration: 4000,
        panelClass: 'error-snackbar'
      })
      return;
    }
    console.log("AREAS ARE OVERLAPPING");
    const {sw: overlapSW, ne: overlapNE} = overlap;
    const swProj = this.coordService.fromLatLng(
      overlapSW.lat,
      overlapSW.lng,
      this.currentZone
    );
    const neProj = this.coordService.fromLatLng(
      overlapNE.lat,
      overlapNE.lng,
      this.currentZone
    );
    if (
      this.xMin === this.roundMin(swProj.x) &&
      this.yMin === this.roundMin(swProj.y) &&
      this.xMax === this.roundMax(neProj.x) &&
      this.yMax === this.roundMax(neProj.y)) {
      this.snackBar.open('AOI already best match.', 'OK', {
        duration: 3000,
        panelClass: 'info-snackbar'
      })
      return;
    }
    this.xMin = this.roundMin(swProj.x);
    this.yMin = this.roundMin(swProj.y);
    this.xMax = this.roundMax(neProj.x);
    this.yMax = this.roundMax(neProj.y);

    this.updatePreview();
    this.snackBar.open('AOI grid adjusted to best overlapping match.', 'OK', {
      duration: 3000,
      panelClass: 'success-snackbar'
    })
  }

  ngAfterViewInit() {
    if (this.currentZone) {
      this.initMap();
    }
  }

  private areasOverlap(): boolean {
    if (this.otherItem === null) return false;
    if (this.xMin === null || this.xMax === null || this.yMin === null || this.yMax === null) {
      return false;
    }
    const overlapsX = this.selectedItem.minX < this.otherItem.maxX && this.selectedItem.maxX > this.otherItem.minX;
    const overlapsY = this.selectedItem.minY < this.otherItem.maxY && this.selectedItem.maxY > this.otherItem.minY;
    return overlapsX && overlapsY;
  }

  private initMap(): void {
    if (!this.currentZone) return;

    if (this.map) {
      this.map.remove();
      this.referenceRect = undefined!;
      this.userRect = undefined!;
      this.otherRect = undefined!;
    }
    // Center map on the Reference AOI (using the reference zone)
    const center = this.coordService.toLatLng(
      (this.selectedItem.minX + this.selectedItem.maxX) / 2,
      (this.selectedItem.minY + this.selectedItem.maxY) / 2,
      this.currentZone
    );

    this.map = L.map('map', {scrollWheelZoom: true, zoomControl: true}).setView(center, 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
    }).addTo(this.map);
    setTimeout(() => {
      this.map.invalidateSize();
      this.drawReferenceRect();
      this.drawOtherRect();
      this.updatePreview();
    }, 200);
  }

  // 1. Draw the static Reference AOI
  private drawReferenceRect(): void {
    if (!this.currentZone) return;

    const sw = this.coordService.toLatLng(this.selectedItem.minX, this.selectedItem.minY, this.currentZone);
    const ne = this.coordService.toLatLng(this.selectedItem.maxX, this.selectedItem.maxY, this.currentZone);
    const bounds = L.latLngBounds(sw, ne);

    this.referenceRect = L.rectangle(bounds, {
      color: '#757575', // Grey
      weight: 2,
      dashArray: '5, 10',
      fill: false,
      interactive: false
    }).addTo(this.map);

    this.map.fitBounds(bounds, {padding: [20, 20]});
  }

  // 2. Draw the Other Item AOI (Handling different EPSG)
  private drawOtherRect(): void {
    if (!this.otherItem) return;

    // Determine the specific zone for this item (might differ from currentZone)
    const otherZone = this.otherItem.epsg || 31256;

    if (!otherZone) {
      // Use console warn if zone is unknown, or we could try to fallback (though risky)
      console.warn(`Could not determine zone for other item (EPSG: ${this.otherItem.epsg})`);
      return;
    }

    // Convert coordinates using the *other* item's zone
    const sw = this.coordService.toLatLng(this.otherItem.minX, this.otherItem.minY, otherZone);
    const ne = this.coordService.toLatLng(this.otherItem.maxX, this.otherItem.maxY, otherZone);
    const bounds = L.latLngBounds(sw, ne);

    this.otherRect = L.rectangle(bounds, {
      color: '#d30000',
      weight: 2,
      dashArray: '5, 10',
      fill: false,
      interactive: false
    }).addTo(this.map);

    this.otherRect.bindTooltip(
      `Other: ${this.otherItem.name} (${otherZone})`,
      {direction: 'top'}
    );
  }

  updatePreview(): void {
    if (this.currentZone && this.xMin !== null && this.xMax !== null && this.yMin !== null && this.yMax !== null) {
      try {
        const sw = this.coordService.toLatLng(this.xMin, this.yMin, this.currentZone);
        const ne = this.coordService.toLatLng(this.xMax, this.yMax, this.currentZone);
        const bounds = L.latLngBounds(sw, ne);
        console.log("SW: " + sw)
        console.log("NE: " + ne)
        if (this.userRect) {
          this.userRect.setBounds(bounds);
        } else {
          this.userRect = L.rectangle(bounds, {
            color: '#3f51b5', // Primary Blue
            weight: 3,
            fill: true,
            fillOpacity: 0.1
          }).addTo(this.map);
        }
      } catch (e) {
      }
    }
  }

  private roundMin(val: number): number {
    return Math.ceil(val * 100) / 100;
  }

  private roundMax(val: number): number {
    return Math.floor(val * 100) / 100;
  }
}
