CREATE TABLE access_requests (
    id          uuid PRIMARY KEY,
    email       varchar(254) NOT NULL,
    company     varchar(120),
    message     varchar(1000),
    created_at  timestamptz NOT NULL
);

CREATE UNIQUE INDEX access_requests_email_unique ON access_requests (lower(email));
