CREATE TABLE organizations (
    id          uuid PRIMARY KEY,
    name        varchar(120) NOT NULL,
    created_at  timestamptz NOT NULL
);

CREATE TABLE users (
    id                      uuid PRIMARY KEY,
    organization_id         uuid NOT NULL,
    email                   varchar(254) NOT NULL,
    password_hash           varchar(100),
    role                    varchar(16) NOT NULL,
    failed_login_attempts   int NOT NULL DEFAULT 0,
    locked_until            timestamptz,
    created_at              timestamptz NOT NULL,
    CONSTRAINT users_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT users_role_check CHECK (role IN ('MEMBER', 'DEMO'))
);

CREATE UNIQUE INDEX users_email_unique ON users (lower(email));
