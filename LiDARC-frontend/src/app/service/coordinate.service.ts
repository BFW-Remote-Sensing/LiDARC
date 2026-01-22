import { Injectable } from '@angular/core';
import proj4 from 'proj4';

@Injectable({ providedIn: 'root' })
export class CoordinateService {
  private readonly WGS84 = "EPSG:4326";

  constructor() {
    // Register Austrian BMN (Bundesmeldenetz) projections once
    proj4.defs([
      ["EPSG:31254", "+proj=tmerc +lat_0=0 +lon_0=10.33333333333333 +k=1 +x_0=0 +y_0=-5000000 +ellps=bessel +towgs84=577.3,90.1,463.9,5.137,1.474,5.297,2.4232 +units=m +no_defs"],
      ["EPSG:31255", "+proj=tmerc +lat_0=0 +lon_0=13.33333333333333 +k=1 +x_0=0 +y_0=-5000000 +ellps=bessel +towgs84=577.3,90.1,463.9,5.137,1.474,5.297,2.4232 +units=m +no_defs"],
      ["EPSG:31256", "+proj=tmerc +lat_0=0 +lon_0=16.33333333333333 +k=1 +x_0=0 +y_0=-5000000 +ellps=bessel +towgs84=577.3,90.1,463.9,5.137,1.474,5.297,2.4232 +units=m +no_defs"]
    ]);
  }

  /**
   * Converts local coordinates to WGS84 Lat/Lng
   * @param epsg The numeric EPSG code (e.g., 31256)
   */
  toLatLng(x: number, y: number, epsg: number = 31256): [number, number] {
    try {
      const [lng, lat] = proj4(`EPSG:${epsg}`, this.WGS84, [x, y]);
      return [lat, lng];
    } catch (error) {
      console.error(`Projection failed for EPSG:${epsg}`, error);
      return [0, 0];
    }
  }
}
