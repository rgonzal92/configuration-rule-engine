import { Routes } from '@angular/router';

// Each page loads on first visit, so the home page does not download the catalog editor.
export const routes: Routes = [
  {
    path: '',
    title: 'Configuration Rule Engine',
    loadComponent: () => import('./features/home/home-page').then((page) => page.HomePage),
  },
  {
    path: 'workspace',
    title: 'Your workspace · Configuration Rule Engine',
    loadComponent: () =>
      import('./features/workspace/workspace-page').then((page) => page.WorkspacePage),
  },
  {
    path: 'showcase',
    title: 'Catalog examples · Configuration Rule Engine',
    loadComponent: () =>
      import('./features/showcase/showcase-page').then((page) => page.ShowcasePage),
  },
];
