export interface CreateReportDto {
  title: string,
  components: ReportComponentDto[]
}
export interface ReportComponentDto {
  type: string,
  fileName: string
}
export interface ChartData {
  name: string,
  fileName: string,
  blob: Blob
}
