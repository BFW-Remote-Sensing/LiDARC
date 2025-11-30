import { Routes } from '@angular/router';
import {Heatmap} from './features/heatmap/heatmap';

export const routes: Routes = [
  { path: 'viewer', component: Heatmap },
];



import { UploadComponent } from './components/upload/upload.component';
export const routes: Routes = [{ path: 'upload', component: UploadComponent }];
