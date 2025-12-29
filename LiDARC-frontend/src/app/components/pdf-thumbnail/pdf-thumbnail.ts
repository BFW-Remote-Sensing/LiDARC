import {AfterViewInit, ChangeDetectorRef, Component, ElementRef, Input, ViewChild} from '@angular/core';
import * as pdfjsLib from 'pdfjs-dist';

pdfjsLib.GlobalWorkerOptions.workerSrc = './assets/pdf.worker.min.mjs';

@Component({
  selector: 'app-pdf-thumbnail',
  imports: [],
  templateUrl: './pdf-thumbnail.html',
  styleUrl: './pdf-thumbnail.scss',
})
export class PdfThumbnail implements AfterViewInit {
  @Input({required: true}) pdfUrl!: string;
  @ViewChild('pdfCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  loading = true;

  constructor(private cdr: ChangeDetectorRef) {
  }

  ngAfterViewInit(): void {
    this.renderPdf();
  }

  private async renderPdf() {
    try {
      const loadingTask = pdfjsLib.getDocument(this.pdfUrl);
      const pdf = await loadingTask.promise;
      const page = await pdf.getPage(1);

      const canvas = this.canvasRef.nativeElement;
      const context = canvas.getContext('2d');

      if (!context) throw new Error('Could not get 2D context');

      const desiredWidth = 300;
      const viewport = page.getViewport({scale: 1});
      const scale = desiredWidth / viewport.width;
      const scaledViewport = page.getViewport({scale});

      canvas.height = scaledViewport.height;
      canvas.width = scaledViewport.width;

      await page.render({
        canvasContext: context,
        viewport: scaledViewport,
        canvas: canvas
      }).promise;

      this.loading = false;
      this.cdr.detectChanges(); // Update the view

    } catch (error) {
      console.error('Error rendering PDF thumbnail', error);
      this.loading = false;
      this.cdr.detectChanges();
    }
  }
}
