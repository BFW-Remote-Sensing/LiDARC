CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    filename TEXT NOT NULL UNIQUE,
    creation_year SMALLINT CHECK (creation_year BETWEEN 1900 AND 9999),
    size_bytes BIGINT NOT NULL,
    min_x DOUBLE PRECISION,
    min_y DOUBLE PRECISION,
    min_z DOUBLE PRECISION,
    min_gpstime DOUBLE PRECISION,
    max_x DOUBLE PRECISION,
    max_y DOUBLE PRECISION,
    max_z DOUBLE PRECISION,
    max_gpstime DOUBLE PRECISION,
    coordinate_system INTEGER NOT NULL,
    las_version VARCHAR(32),
    capture_software VARCHAR(128),
    uploaded BOOLEAN DEFAULT FALSE,
    uploaded_at TIMESTAMP
);


CREATE TABLE IF NOT EXISTS urls (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    file_id INTEGER NOT NULL,
    s3_bucket TEXT NOT NULL DEFAULT 'basebucket',
    s3_url TEXT NOT NULL,
    presigned BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP
);



CREATE TABLE IF NOT EXISTS coordinate_system (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    coordinate_system VARCHAR(50) NOT NULL CHECK (coordinate_system IN ('WGS84','31256','LOCAL','UNKNOWN')),
    coordinate_system_prefix VARCHAR(50) NOT NULL CHECK (coordinate_system_prefix IN ('EPSG', 'LOCAL'))
);

INSERT INTO coordinate_system (id, coordinate_system, coordinate_system_prefix)
VALUES (31256, '31256', 'EPSG')
ON CONFLICT (id) DO NOTHING;


ALTER TABLE files 
ADD CONSTRAINT fk_files_coordinate_system FOREIGN KEY (coordinate_system) REFERENCES coordinate_system(id);

ALTER TABLE urls
ADD CONSTRAINT fk_url_files FOREIGN KEY (file_id) REFERENCES files(id);
