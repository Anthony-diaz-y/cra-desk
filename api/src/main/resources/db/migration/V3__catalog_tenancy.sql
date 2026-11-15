ALTER TABLE products ADD COLUMN organization_id uuid;

INSERT INTO organizations (id, name, created_at)
SELECT '00000000-0000-0000-0000-000000000001', 'Unassigned (pre-V3 data)', now()
WHERE EXISTS (SELECT 1 FROM products);

UPDATE products
SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE products ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE products
    ADD CONSTRAINT products_organization_fk FOREIGN KEY (organization_id) REFERENCES organizations (id);

DROP INDEX products_name_unique;

CREATE UNIQUE INDEX products_org_name_unique ON products (organization_id, lower(name));

ALTER TABLE product_versions
    RENAME CONSTRAINT product_versions_product_id_version_key TO product_versions_product_version_unique;
