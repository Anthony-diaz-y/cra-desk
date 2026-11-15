CREATE TABLE version_scans (
    product_version_id      uuid PRIMARY KEY,
    status                  varchar(16) NOT NULL,
    requested_at            timestamptz NOT NULL,
    started_at              timestamptz,
    finished_at             timestamptz,
    error                   varchar(500),
    components_scanned      int,
    components_not_scanned  int,
    CONSTRAINT version_scans_version_fk FOREIGN KEY (product_version_id) REFERENCES product_versions (id),
    CONSTRAINT version_scans_status_check CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

CREATE INDEX version_scans_status_idx ON version_scans (status, requested_at);

CREATE TABLE vulnerabilities (
    id          varchar(100) PRIMARY KEY,
    summary     varchar(1000),
    severity    varchar(200),
    aliases     text[] NOT NULL,
    published   timestamptz,
    modified    timestamptz NOT NULL,
    fetched_at  timestamptz NOT NULL
);

CREATE TABLE kev_entries (
    cve_id              varchar(30) PRIMARY KEY,
    vendor_project      varchar(200),
    product             varchar(200),
    vulnerability_name  varchar(500),
    date_added          date NOT NULL,
    due_date            date,
    known_ransomware    varchar(20),
    short_description   varchar(2000),
    fetched_at          timestamptz NOT NULL
);

CREATE TABLE vulnerability_matches (
    id                  uuid PRIMARY KEY,
    product_version_id  uuid NOT NULL,
    component_id        uuid NOT NULL,
    vulnerability_id    varchar(100) NOT NULL,
    kev                 boolean NOT NULL,
    CONSTRAINT vulnerability_matches_version_fk FOREIGN KEY (product_version_id) REFERENCES product_versions (id),
    CONSTRAINT vulnerability_matches_component_fk FOREIGN KEY (component_id) REFERENCES sbom_components (id)
        ON DELETE CASCADE,
    CONSTRAINT vulnerability_matches_vulnerability_fk FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id),
    CONSTRAINT vulnerability_matches_unique UNIQUE (component_id, vulnerability_id)
);

CREATE INDEX vulnerability_matches_version_idx ON vulnerability_matches (product_version_id);

CREATE TABLE findings (
    id                  uuid PRIMARY KEY,
    organization_id     uuid NOT NULL,
    product_version_id  uuid NOT NULL,
    vulnerability_id    varchar(100) NOT NULL,
    cve_id              varchar(30) NOT NULL,
    status              varchar(20) NOT NULL,
    present             boolean NOT NULL,
    created_at          timestamptz NOT NULL,
    last_seen_at        timestamptz NOT NULL,
    CONSTRAINT findings_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT findings_version_fk FOREIGN KEY (product_version_id) REFERENCES product_versions (id),
    CONSTRAINT findings_vulnerability_fk FOREIGN KEY (vulnerability_id) REFERENCES vulnerabilities (id),
    CONSTRAINT findings_status_check CHECK (status IN ('PENDING_REVIEW')),
    CONSTRAINT findings_version_vuln_unique UNIQUE (product_version_id, vulnerability_id)
);

CREATE INDEX findings_organization_status_idx ON findings (organization_id, status);
