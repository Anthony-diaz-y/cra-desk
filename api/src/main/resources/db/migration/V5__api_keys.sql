CREATE TABLE api_keys (
    id               uuid PRIMARY KEY,
    organization_id  uuid NOT NULL,
    created_by       uuid NOT NULL,
    name             varchar(80) NOT NULL,
    prefix           varchar(8) NOT NULL,
    key_hash         varchar(64) NOT NULL,
    created_at       timestamptz NOT NULL,
    last_used_at     timestamptz,
    revoked_at       timestamptz,
    CONSTRAINT api_keys_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT api_keys_user_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT api_keys_prefix_unique UNIQUE (prefix)
);
