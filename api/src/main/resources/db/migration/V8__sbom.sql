CREATE TABLE sboms (
    id                      uuid PRIMARY KEY,
    product_version_id      uuid NOT NULL,
    spec_version            varchar(8) NOT NULL,
    serial_number           varchar(100),
    bom_version             int,
    sha256                  varchar(64) NOT NULL,
    size_bytes              int NOT NULL,
    component_count         int NOT NULL,
    components_without_purl int NOT NULL,
    document                text NOT NULL,
    uploaded_by             uuid,
    uploaded_via            varchar(8) NOT NULL,
    uploaded_at             timestamptz NOT NULL,
    CONSTRAINT sboms_version_fk FOREIGN KEY (product_version_id) REFERENCES product_versions (id),
    CONSTRAINT sboms_version_unique UNIQUE (product_version_id),
    CONSTRAINT sboms_user_fk FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT sboms_uploaded_via_check CHECK (uploaded_via IN ('WEB', 'CLI'))
);

CREATE TABLE sbom_components (
    id          uuid PRIMARY KEY,
    sbom_id     uuid NOT NULL,
    bom_ref     varchar(1024),
    type        varchar(32),
    group_name  varchar(512),
    name        varchar(512) NOT NULL,
    version     varchar(256),
    purl        varchar(2048),
    CONSTRAINT sbom_components_sbom_fk FOREIGN KEY (sbom_id) REFERENCES sboms (id) ON DELETE CASCADE
);

CREATE INDEX sbom_components_sbom_idx ON sbom_components (sbom_id);

CREATE INDEX sbom_components_purl_idx ON sbom_components (purl);
