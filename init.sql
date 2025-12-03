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



ALTER TABLE files 
ADD CONSTRAINT fk_files_coordinate_system FOREIGN KEY (coordinate_system) REFERENCES coordinate_system(id);

ALTER TABLE urls
ADD CONSTRAINT fk_url_files FOREIGN KEY (file_id) REFERENCES files(id);
