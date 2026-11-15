CREATE TABLE invitations (
    id           uuid PRIMARY KEY,
    email        varchar(254) NOT NULL,
    code_hash    varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    accepted_at  timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT invitations_code_hash_unique UNIQUE (code_hash)
);
