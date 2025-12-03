import { Component, signal } from '@angular/core';
import { RouterModule, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { Navbar } from './components/navbar/navbar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatButtonModule, Navbar, RouterModule,],
  standalone: true,
  templateUrl: './app.html',
  styleUrls: ['./app.scss'],
})

export class App {
  protected readonly title = signal('LiDARC-frontend');
}
