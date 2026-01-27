import {VegetationStatsWithoutCells} from './chunking';

export interface CreateReportDto {
  title: string,
  components: ReportComponentDto[],
  stats?: VegetationStatsWithoutCells
}

export interface ReportComponentDto {
  type: ReportType,
  fileName: string
}

export interface ChartData {
  name: string,
  fileName: string,
  type?: ReportType,
  files: {
    fileName: string;
    blob: Blob
  }[];
}

export enum ReportType {
  SIMPLE = "SIMPLE",
  HEATMAP = "HEATMAP",
  SIDE_BY_SIDE = "SIDE_BY_SIDE",
  BOXPLOT = "BOXPLOT",
  DISTRIBUTION = "DISTRIBUTION",
  DISTRIBUTION_DIFF = "DISTRIBUTION_DIFF",
  HISTO = "HISTO",
  SCATTER = "SCATTER"
}
