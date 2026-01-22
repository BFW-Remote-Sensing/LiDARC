import {Component, Inject, OnInit, AfterViewInit, inject} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialogModule } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import * as L from 'leaflet';
import { CoordinateService } from '../../service/coordinate.service';
import { CommonModule } from '@angular/common';

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
    MatSelectModule
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
  selectedItem: AOIItem;
  otherItem: AOIItem | null = null; // Store the non-selected item
  currentZone: number;

  constructor(
    public dialogRef: MatDialogRef<DefineGrid>,
    @Inject(MAT_DIALOG_DATA) public data: { item: AOIItem, otherItem?: AOIItem }
  ) {
    this.selectedItem = this.data.item;
    this.otherItem = this.data.otherItem || null;
    this.currentZone = this.selectedItem.epsg || 31256;
    this.xMin = Math.ceil(this.selectedItem.minX * 100) / 100;
    this.xMax = Math.floor(this.selectedItem.maxX * 100) / 100;
    this.yMin = Math.ceil(this.selectedItem.minY * 100) / 100;
    this.yMax = Math.floor(this.selectedItem.maxY * 100) / 100;
  }

  ngOnInit() {
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onDefine(): void {
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
        zone: this.currentZone
      });
    }
  }

  ngAfterViewInit() {
    if (this.currentZone) {
      this.initMap();
    }
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

    this.map = L.map('map', { scrollWheelZoom: false }).setView(center, 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
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

    this.map.fitBounds(bounds, { padding: [20, 20] });
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
      { direction: 'top' }
    );
  }

  updatePreview(): void {
    if (this.currentZone && this.xMin !== null && this.xMax !== null && this.yMin !== null && this.yMax !== null) {
      try {
        const sw = this.coordService.toLatLng(this.xMin, this.yMin, this.currentZone);
        const ne = this.coordService.toLatLng(this.xMax, this.yMax, this.currentZone);
        const bounds = L.latLngBounds(sw, ne);

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
}
