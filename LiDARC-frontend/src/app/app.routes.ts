import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'upload', pathMatch: 'full' },
  {
    path: 'upload',
    loadComponent: () => import('./components/upload/upload.component').then(m => m.UploadComponent)
  },
  {
    path: 'unassigned-files',
    loadComponent: () => import('./components/stored-files/stored-files').then(m => m.StoredFiles)
  },
  {
    path: 'comparable-items',
    loadComponent: () => import('./components/comparable-items/comparable-items').then(m => m.ComparableItems)
  },
  {
    path: 'stored-files/:id',
    loadComponent: () => import('./components/file-details/file-details').then(m => m.FileDetails)
  },
  {
    path: 'folders/:id',
    loadComponent: () => import('./components/folder-details/folder-details').then(m => m.FolderDetails)
  },
  {
    path: 'comparisons',
    loadComponent: () => import('./components/comparisons/comparisons').then(m => m.Comparisons)
  },
  {
    path: 'comparisons/:id',
    loadComponent: () => import('./components/comparison-details/comparison-details').then(m => m.ComparisonDetails)
  },
  {
    path: 'comparison-setup',
    loadComponent: () => import('./components/comparison-setup/comparison-setup').then(m => m.ComparisonSetup)
  },
  {
    path: 'reports',
    loadComponent: () => import('./components/reports/reports').then(m => m.Reports)
  },
];
