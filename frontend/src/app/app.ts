import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Provides the shared application shell and primary navigation. */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
