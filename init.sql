CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    filename TEXT NOT NULL UNIQUE,
    capture_year SMALLINT CHECK (capture_year BETWEEN 1900 AND 9999),
    size_bytes BIGINT,
    original_filename TEXT,
    min_x DOUBLE PRECISION,
    min_y DOUBLE PRECISION,
    min_z DOUBLE PRECISION,
    max_x DOUBLE PRECISION,
    max_y DOUBLE PRECISION,
    max_z DOUBLE PRECISION,
    coordinate_system INTEGER,
    system_identifier TEXT,
    las_version VARCHAR(32),
    capture_software VARCHAR(128),
    point_count BIGINT,
    file_creation_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' CHECK (status in ('UPLOADED', 'PROCESSING', 'PROCESSED', 'FAILED')),
    uploaded BOOLEAN DEFAULT FALSE,
    uploaded_at TIMESTAMP
);


CREATE TABLE IF NOT EXISTS urls (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    file_id INTEGER NOT NULL,
    s3_bucket TEXT DEFAULT 'basebucket',
    s3_url TEXT NOT NULL,
    presigned BOOLEAN DEFAULT TRUE,
    method VARCHAR(10) NOT NULL CHECK (method in ('GET', 'PUT')),
    expires_at TIMESTAMP,
    created_at TIMESTAMP
);



CREATE TABLE IF NOT EXISTS coordinate_system (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    authority VARCHAR(50) NOT NULL,
    code VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS comparisons (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name TEXT NOT NULL,
    need_highest_vegetation BOOLEAN DEFAULT FALSE,
    need_outlier_detection BOOLEAN DEFAULT FALSE,
    need_statistics_over_scenery BOOLEAN DEFAULT FALSE,
    need_most_differences BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status in ('PENDING', 'COMPLETED', 'FAILED')),
    result_report_url TEXT,
    error_message TEXT,
    grid_cell_width INTEGER,
    grid_cell_height INTEGER,
    grid_min_x DOUBLE PRECISION,
    grid_max_x DOUBLE PRECISION,
    grid_min_y DOUBLE PRECISION,
    grid_max_y DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS comparison_file (
    comparison_id INTEGER NOT NULL,
    file_id INTEGER NOT NULL,
    CONSTRAINT pk_comparison_file PRIMARY KEY (comparison_id, file_id),
    CONSTRAINT fk_comparison_id FOREIGN KEY (comparison_id) REFERENCES comparisons(id) ON DELETE CASCADE,
    CONSTRAINT fk_file_id FOREIGN KEY (file_id) REFERENCES files(id)
);

ALTER TABLE files 
ADD CONSTRAINT fk_files_coordinate_system FOREIGN KEY (coordinate_system) REFERENCES coordinate_system(id);

ALTER TABLE urls
ADD CONSTRAINT fk_url_files FOREIGN KEY (file_id) REFERENCES files(id);
