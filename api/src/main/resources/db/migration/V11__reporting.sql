ALTER TABLE findings DROP CONSTRAINT findings_status_check;

ALTER TABLE findings
    ADD CONSTRAINT findings_status_check CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'DISMISSED'));

ALTER TABLE findings
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN reviewed_by uuid,
    ADD COLUMN dismiss_reason varchar(1000);

CREATE TABLE reportable_events (
    id                  uuid PRIMARY KEY,
    organization_id     uuid NOT NULL,
    finding_id          uuid NOT NULL,
    product_version_id  uuid NOT NULL,
    cve_id              varchar(30) NOT NULL,
    confirmed_by        uuid,
    confirmed_at        timestamptz NOT NULL,
    CONSTRAINT reportable_events_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT reportable_events_finding_fk FOREIGN KEY (finding_id) REFERENCES findings (id),
    CONSTRAINT reportable_events_version_fk FOREIGN KEY (product_version_id) REFERENCES product_versions (id),
    CONSTRAINT reportable_events_user_fk FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT reportable_events_finding_unique UNIQUE (finding_id)
);

CREATE INDEX reportable_events_organization_idx ON reportable_events (organization_id, confirmed_at DESC);

CREATE TABLE reports (
    id               uuid PRIMARY KEY,
    event_id         uuid NOT NULL,
    type             varchar(20) NOT NULL,
    due_at           timestamptz NOT NULL,
    draft_status     varchar(10) NOT NULL,
    content          text,
    generated_by     varchar(20),
    ai_error         varchar(300),
    approved_by      uuid,
    approved_at      timestamptz,
    sent_at          timestamptz,
    sent_by          uuid,
    enisa_reference  varchar(100),
    updated_at       timestamptz NOT NULL,
    CONSTRAINT reports_event_fk FOREIGN KEY (event_id) REFERENCES reportable_events (id) ON DELETE CASCADE,
    CONSTRAINT reports_event_type_unique UNIQUE (event_id, type),
    CONSTRAINT reports_type_check CHECK (type IN ('EARLY_WARNING', 'NOTIFICATION', 'FINAL_REPORT')),
    CONSTRAINT reports_draft_status_check CHECK (draft_status IN ('EMPTY', 'DRAFTED', 'APPROVED'))
);

CREATE TABLE ai_usage (
    organization_id  uuid NOT NULL,
    day              date NOT NULL,
    count            int NOT NULL,
    CONSTRAINT ai_usage_pk PRIMARY KEY (organization_id, day),
    CONSTRAINT ai_usage_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id)
);
