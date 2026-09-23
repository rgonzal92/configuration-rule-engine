import { Component } from '@angular/core';
import { Routes } from '@angular/router';

/** Explains where catalog editing will appear. */
@Component({ template: '<p>Catalog editing will be available here.</p>' })
export class WorkspacePage {}

/** Explains where public examples will appear. */
@Component({ template: '<p>Public catalog examples will be available here.</p>' })
export class ShowcasePage {}

export const routes: Routes = [
  { path: 'workspace', component: WorkspacePage },
  { path: 'showcase', component: ShowcasePage },
];
