import { Routes } from '@angular/router';
import { Heatmap } from './components/heatmap/heatmap';
import { UploadComponent } from './components/upload/upload.component';
import { StoredFiles } from './components/stored-files/stored-files';
import { Comparisons } from './components/comparisons/comparisons';
import { FileDetails } from './components/file-details/file-details';
import { ComparisonDetails } from './components/comparison-details/comparison-details';
import { ComparisonSetup } from './components/comparison-setup/comparison-setup';
import { ComparableItems } from './components/comparable-items/comparable-items';
import { FolderDetails } from './components/folder-details/folder-details';
import {Reports} from './components/reports/reports';

export const routes: Routes = [
  { path: '', redirectTo: 'upload', pathMatch: 'full' },
  { path: 'upload', component: UploadComponent },
  { path: 'unassigned-files', component: StoredFiles },
  { path: 'comparable-items', component: ComparableItems },
  { path: 'stored-files/:id', component: FileDetails },
  { path: 'folders/:id', component: FolderDetails },
  { path: 'comparisons', component: Comparisons },
  { path: 'comparisons/:id', component: ComparisonDetails },
  { path: 'comparison-setup', component: ComparisonSetup },
  {path: 'reports', component: Reports},
];
