export interface ChunkedCell {
  x0: number;
  x1: number;
  y0: number;
  y1: number;
  veg_height_max_a: number;
  veg_height_max_b: number;
  delta_z: number;
  [key: string]: number;
}

export interface Percentiles {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
}

export interface FileMetrics {
  mean_veg_height: number;
  median_veg_height: number;
  std_veg_height: number;
  min_veg_height: number;
  max_veg_height: number;
  percentiles: Percentiles;
  mean_points_per_grid_cell: number;
}

export interface DifferenceMetrics {
  mean: number;
  median: number;
  std: number;
  most_negative: number;
  least_negative: number;
  smallest_positive: number;
  largest_positive: number;
  correlation: CorrelationMetrics;
  histogram: Histogram;
}

export interface CategorizedCounts {
  almost_equal: number;
  slightly_different: number;
  different: number;
  highly_different: number;
}

export interface ChunkingResult {
  comparisonId: number;
  chunkingSize: number;
  chunked_cells: ChunkedCell[][];
  statistics: {
    file_a: FileMetrics;
    file_b: FileMetrics;
    difference: DifferenceMetrics;
    categorized: CategorizedCounts;
  };
  statistics_p?:{
    file_a: FileMetrics;
    file_b: FileMetrics;
    difference: DifferenceMetrics;
  }
  group_mapping: GroupMapping;
}

export interface CellEntry {
  A: number;
  B: number;
  delta_z: number;
}

export interface VegetationStats {
  cells: CellEntry[];
  fileA_metrics: FileMetrics;
  fileB_metrics: FileMetrics;
  difference_metrics: DifferenceMetrics;
  group_mapping: GroupMapping;
}
export interface GroupMapping {
  a: string;
  b: string;
}

export interface Histogram {
  bin_edges: number[];
  counts: number[];
}

export interface RegressionLine {
  slope: number;
  intercept: number;
  x_min: number;
  x_max: number;
}

export interface CorrelationMetrics {
  pearson_correlation: number;
  regression_line: RegressionLine;
}
