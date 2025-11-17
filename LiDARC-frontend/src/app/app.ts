import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {Heatmap} from './features/heatmap/heatmap';



@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Heatmap],
  templateUrl: './app.html',
  styleUrl: './app.scss',

})

export class App {

  protected readonly title = signal('LiDARC-frontend');

}
