CREATE TABLE audit_chain_heads (
    organization_id uuid PRIMARY KEY,
    last_seq        bigint      NOT NULL,
    last_hash       varchar(64) NOT NULL,
    CONSTRAINT audit_chain_heads_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE audit_entries (
    id              uuid PRIMARY KEY,
    organization_id uuid         NOT NULL,
    seq             bigint       NOT NULL,
    occurred_at     timestamptz  NOT NULL,
    actor_id        uuid,
    actor_role      varchar(20)  NOT NULL,
    action          varchar(60)  NOT NULL,
    target_type     varchar(40)  NOT NULL,
    target_id       varchar(100) NOT NULL,
    details         text         NOT NULL,
    prev_hash       varchar(64)  NOT NULL,
    hash            varchar(64)  NOT NULL,
    CONSTRAINT audit_entries_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT audit_entries_seq_unique UNIQUE (organization_id, seq),
    CONSTRAINT audit_entries_seq_positive CHECK (seq > 0)
);

CREATE FUNCTION audit_entries_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_append_only
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_entries_append_only();
