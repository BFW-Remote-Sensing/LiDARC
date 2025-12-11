import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-text-card',
  imports: [
    MatCardModule,
    MatIconModule,
    NgClass
  ],
  templateUrl: './text-card.html',
  styleUrl: './text-card.scss',
})
export class TextCard {
  @Input() type: 'info' | 'error' = 'info';
  @Input() message: string = '';

  get icon(): string {
    return this.type === 'error' ? 'error_outline' : 'info';
  }

  get cssClass(): string {
    return this.type === 'error' ? 'error-card' : 'info-card';
  }
}
