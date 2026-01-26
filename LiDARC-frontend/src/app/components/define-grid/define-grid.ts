import {Component, Inject, OnInit, AfterViewInit, inject, ChangeDetectorRef, ViewChild} from '@angular/core';
import {MatDialogRef, MAT_DIALOG_DATA} from '@angular/material/dialog';
import {MatDialogModule} from '@angular/material/dialog';
import {NgModel, FormsModule } from '@angular/forms';
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
  private referenceRect!: L.Rectangle;
  private otherRect!: L.Rectangle;
  private userRect!: L.Rectangle;
  private coordService = inject(CoordinateService);
  private swHandle!: L.Marker;
  private nwHandle!: L.Marker;
  private neHandle!: L.Marker;
  private seHandle!: L.Marker;
  private cdr = inject(ChangeDetectorRef);
  @ViewChild('xMinModel') xMinModel!: NgModel;
  @ViewChild('xMaxModel') xMaxModel!: NgModel;
  @ViewChild('yMinModel') yMinModel!: NgModel;
  @ViewChild('yMaxModel') yMaxModel!: NgModel;
  limitXMin!: number;
  limitXMax!: number;
  limitYMin!: number;
  limitYMax!: number;

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
  otherItem: AOIItem | null = null;
  currentZone: number;

  constructor(
    public dialogRef: MatDialogRef<DefineGrid>,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { item: AOIItem, otherItem: AOIItem }
  ) {
    this.selectedItem = this.data.item;
    this.otherItem = this.data.otherItem;
    this.currentZone = this.selectedItem.epsg || 31256;
    this.limitXMin = this.roundMin(this.selectedItem.minX);
    this.limitXMax = this.roundMax(this.selectedItem.maxX);
    this.limitYMin = this.roundMin(this.selectedItem.minY);
    this.limitYMax = this.roundMax(this.selectedItem.maxY);

    this.xMin = this.limitXMin;
    this.xMax = this.limitXMax;
    this.yMin = this.limitYMin;
    this.yMax = this.limitYMax;

    // Maintain global extremes for dialog return values
    if (this.otherItem) {
      this.maxXMax = Math.max(this.xMax, this.otherItem.maxX);
      this.maxYMax = Math.max(this.yMax, this.otherItem.maxY);
      this.minXMin = Math.min(this.xMin, this.otherItem.minX);
      this.minYMin = Math.min(this.yMin, this.otherItem.minY);
    }
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

    // 2. Create a custom "beacon" icon for the center
    const otherIcon = L.divIcon({
      className: 'leaflet-interactive',
      html: `<div style="
        width: 16px;
        height: 16px;
        background-color: #d30000;
        border: 3px solid white;
        border-radius: 50%;
        box-shadow: 0 3px 6px rgba(0,0,0,0.6);
      "></div>`,
      iconSize: [22, 22],
      iconAnchor: [11, 11]
    });

    const nw = this.coordService.toLatLng(this.otherItem.minX, this.otherItem.maxY, otherZone);
    const se = this.coordService.toLatLng(this.otherItem.maxX, this.otherItem.minY, otherZone);

    // 3. Add the marker to the center of the bounds
    L.marker(sw, { icon: otherIcon, interactive: false }).addTo(this.map);
    L.marker(ne, { icon: otherIcon, interactive: false }).addTo(this.map);
    L.marker(nw, { icon: otherIcon, interactive: false }).addTo(this.map);
    L.marker(se, { icon: otherIcon, interactive: false }).addTo(this.map);
  }

  updatePreview(): void {
    if (this.currentZone && this.xMin !== null && this.xMax !== null && this.yMin !== null && this.yMax !== null) {
        const sw = this.coordService.toLatLng(this.xMin, this.yMin, this.currentZone);
        const ne = this.coordService.toLatLng(this.xMax, this.yMax, this.currentZone);
        const nw = this.coordService.toLatLng(this.xMin, this.yMax, this.currentZone);
        const se = this.coordService.toLatLng(this.xMax, this.yMin, this.currentZone);

        const bounds = L.latLngBounds(sw, ne);

        if (this.userRect) {
          this.userRect.setBounds(bounds);
        } else {
          this.userRect = L.rectangle(bounds, {
            color: '#3f51b5', weight: 3, fill: true, fillOpacity: 0.1
          }).addTo(this.map);
        }

        const resizeIcon = L.divIcon({
          className: 'leaflet-interactive',

          html: `<div style="
          width: 16px;
          height: 16px;
          background-color: #3f51b5;
          border: 3px solid white;
          border-radius: 50%;
          box-shadow: 0 3px 6px rgba(0,0,0,0.6);
          cursor: grab;
          pointer-events: auto;
        "></div>`,

          iconSize: [22, 22],
          iconAnchor: [11, 11]
        });

        if (!this.swHandle) {
          this.swHandle = L.marker(sw, { icon: resizeIcon, draggable: true }).addTo(this.map);
          this.neHandle = L.marker(ne, { icon: resizeIcon, draggable: true }).addTo(this.map);
          this.nwHandle = L.marker(nw, { icon: resizeIcon, draggable: true }).addTo(this.map);
          this.seHandle = L.marker(se, { icon: resizeIcon, draggable: true }).addTo(this.map);

          this.swHandle.on('drag', (e) => this.onHandleDrag(e.target.getLatLng(), 'sw'));
          this.neHandle.on('drag', (e) => this.onHandleDrag(e.target.getLatLng(), 'ne'));
          this.nwHandle.on('drag', (e) => this.onHandleDrag(e.target.getLatLng(), 'nw'));
          this.seHandle.on('drag', (e) => this.onHandleDrag(e.target.getLatLng(), 'se'));
        } else {
          this.swHandle.setLatLng(sw);
          this.neHandle.setLatLng(ne);
          this.nwHandle.setLatLng(nw);
          this.seHandle.setLatLng(se);
        }
      }
    }

  private roundMin(val: number): number {
    return Math.ceil(val * 100) / 100;
  }

  private roundMax(val: number): number {
    return Math.floor(val * 100) / 100;
  }

  private onHandleDrag(latlng: L.LatLng, corner: 'sw' | 'ne' | 'nw' | 'se'): void {
    const coords = this.coordService.fromLatLng(latlng.lat, latlng.lng, this.currentZone);
    const newX = Math.round(coords.x * 100) / 100;
    const newY = Math.round(coords.y * 100) / 100;

    if (corner === 'sw') {
      this.xMin = newX;
      this.yMin = newY;
    } else if (corner === 'ne') {
      this.xMax = newX;
      this.yMax = newY;
    } else if (corner === 'nw') {
      this.xMin = newX;
      this.yMax = newY;
    } else if (corner === 'se') {
      this.xMax = newX;
      this.yMin = newY;
    }

    this.updatePreview();
    this.xMinModel?.control.markAsTouched();
    this.xMaxModel?.control.markAsTouched();
    this.yMinModel?.control.markAsTouched();
    this.yMaxModel?.control.markAsTouched();
    this.xMinModel?.control.updateValueAndValidity();
    this.xMaxModel?.control.updateValueAndValidity();
    this.yMinModel?.control.updateValueAndValidity();
    this.yMaxModel?.control.updateValueAndValidity();
    this.cdr.detectChanges();
  }
}
