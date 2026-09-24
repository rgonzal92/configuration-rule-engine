import { Routes } from '@angular/router';
import { ShowcasePage } from './features/showcase/showcase-page';
import { WorkspacePage } from './features/workspace/workspace-page';

export const routes: Routes = [
  { path: 'workspace', component: WorkspacePage },
  { path: 'showcase', component: ShowcasePage },
];
