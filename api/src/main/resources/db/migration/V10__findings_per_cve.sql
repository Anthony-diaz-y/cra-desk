DELETE FROM findings f
USING findings older
WHERE f.product_version_id = older.product_version_id
  AND f.cve_id = older.cve_id
  AND (f.created_at, f.id) > (older.created_at, older.id);

ALTER TABLE findings DROP CONSTRAINT findings_version_vuln_unique;

ALTER TABLE findings ADD CONSTRAINT findings_version_cve_unique UNIQUE (product_version_id, cve_id);
