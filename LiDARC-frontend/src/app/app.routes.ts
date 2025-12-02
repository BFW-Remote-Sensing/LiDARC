import { Routes } from '@angular/router';
import {Heatmap} from './features/heatmap/heatmap';
import { UploadComponent } from './components/upload/upload.component';

export const routes: Routes = [

  { path: 'heatmap', component: Heatmap },
  {path: 'upload', component: UploadComponent},
];
