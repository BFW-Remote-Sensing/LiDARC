import { Routes } from '@angular/router';
import { Heatmap } from './features/heatmap/heatmap';
import { UploadComponent } from './components/upload/upload.component';
import { StoredFiles } from './components/stored-files/stored-files';
import { Comparisons } from './components/comparisons/comparisons';
import { FileDetails } from './components/file-details/file-details';
import { ComparisonDetails } from './components/comparison-details/comparison-details';
import { ComparisonSetup } from './components/comparison-setup/comparison-setup';

export const routes: Routes = [
  { path: '', redirectTo: 'upload', pathMatch: 'full' },
  { path: 'upload', component: UploadComponent },
  { path: 'stored-files', component: StoredFiles },
  { path: 'stored-files/:id', component: FileDetails },
  { path: 'comparisons', component: Comparisons },
  { path: 'comparisons/:id', component: ComparisonDetails },
  { path: 'comparison-setup', component: ComparisonSetup },
  { path: 'heatmap', component: Heatmap },
];
