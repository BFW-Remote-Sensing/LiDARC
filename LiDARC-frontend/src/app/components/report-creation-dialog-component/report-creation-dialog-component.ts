import {Component, Inject} from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle
} from '@angular/material/dialog';
import {ComparisonDTO} from '../../dto/comparison';
import {ComparisonService} from '../../service/comparison.service';
import {ChartData, CreateReportDto, ReportType} from '../../dto/report';
import {MatInputModule} from '@angular/material/input';
import {FormsModule} from '@angular/forms';
import {MatListModule, MatListOption, MatSelectionList} from '@angular/material/list';
import {MatButton} from '@angular/material/button';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-report-creation-dialog-component',
  imports: [
    CommonModule,
    FormsModule,
    MatDialogActions,
    MatFormFieldModule,
    MatDialogContent,
    MatDialogTitle,
    MatInputModule,
    FormsModule,
    MatSelectionList,
    MatListOption,
    MatListModule,
    MatIconModule,
    MatButton,
  ],
  templateUrl: './report-creation-dialog-component.html',
  styleUrl: './report-creation-dialog-component.scss',
})
export class ReportCreationDialogComponent {

  report: CreateReportDto = {title: '', components: []};
  selectedCharts: ChartData[] = [];
  isProcessing = false;

  constructor(
    public dialogRef: MatDialogRef<ReportCreationDialogComponent>,
    public comparisonService: ComparisonService,
    @Inject(MAT_DIALOG_DATA) public data: { comparison: ComparisonDTO, availableCharts: ChartData[] }
  ) {
    console.log(this.data.availableCharts)
    this.selectedCharts = [...this.data.availableCharts];
  }

  onSelectionChange(event: any): void {
    this.selectedCharts = event.source.selectedOptions.selected.map((opt: any) => opt.value)
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onCreate(): void {
    this.isProcessing = true;
    this.report.components = this.selectedCharts.map(chart => ({
      type: chart.type ? chart.type : ReportType.SIMPLE,
      fileName: chart.fileName,
      title: chart.name
    }));
    const filesToSend: File[] = [];
    this.selectedCharts.forEach(chart => {
      if (chart.files && chart.files.length > 0) {
        chart.files.forEach(f => {
          filesToSend.push(new File([f.blob], f.fileName, {type: 'image/png'}));
        });
      }
    });

    this.comparisonService.createReport(this.data.comparison.id, this.report, filesToSend)
      .subscribe({
        next: res => {
          this.isProcessing = false;
          this.downloadFile(res);
          this.dialogRef.close(true);
        },
        error: err => {
          this.isProcessing = false;
          console.error('Report generation failed', err);
        }
      });
  }

  private downloadFile(data: Blob): void {
    const blob = new Blob([data], {type: 'application/pdf'});
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `report_${this.report.title}.pdf`;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
