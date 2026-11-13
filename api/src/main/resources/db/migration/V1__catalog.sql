CREATE TABLE products (
    id              uuid PRIMARY KEY,
    name            varchar(120) NOT NULL,
    description     varchar(2000),
    product_class   varchar(32) NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);

CREATE UNIQUE INDEX products_name_unique ON products (lower(name));

CREATE TABLE product_versions (
    id              uuid PRIMARY KEY,
    product_id      uuid NOT NULL REFERENCES products(id),
    version         varchar(64) NOT NULL,
    released_at     date,
    created_at      timestamptz NOT NULL,
    UNIQUE (product_id, version)
);