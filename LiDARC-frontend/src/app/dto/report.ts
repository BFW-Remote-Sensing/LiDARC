export interface CreateReportDto {
  title: string,
  components: ReportComponentDto[]
}

export interface ReportComponentDto {
  type: ReportType,
  fileName: string
}

export interface ChartData {
  name: string,
  fileName: string,
  type?: ReportType,
  blob: Blob
}

export enum ReportType {
  SIMPLE = "SIMPLE",
  HEATMAP = "HEATMAP",
  BOXPLOT = "BOXPLOT",
  DISTRIBUTION = "DISTRIBUTION",
  HISTO = "HISTO",
  SCATTER = "SCATTER"
}
