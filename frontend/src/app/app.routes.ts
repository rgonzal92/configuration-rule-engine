import { Routes } from '@angular/router';
import { ShowcasePage } from './showcase-page';
import { WorkspacePage } from './workspace-page';

export const routes: Routes = [
  { path: 'workspace', component: WorkspacePage },
  { path: 'showcase', component: ShowcasePage },
];
