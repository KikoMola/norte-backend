-- Empresas (paso 0.22). Multi-tenant con RLS (paso 0.16):
--  * tenant_id es la primera columna de la clave primaria y de cada índice.
--  * FORCE ROW LEVEL SECURITY hace que la política se aplique también al dueño de la tabla.
--  * El tenant llega en app.tenant_id, fijado con set_config(..., true) al empezar cada transacción.
--    NULLIF evita el error de convertir '' a uuid cuando una conexión del pool ya tuvo un valor local.

CREATE TABLE parties.company
(
  tenant_id  uuid         NOT NULL,
  id         uuid         NOT NULL,
  name       varchar(200) NOT NULL,
  tax_id     varchar(15)  NOT NULL,
  version    bigint       NOT NULL,
  created_at timestamptz  NOT NULL,
  updated_at timestamptz  NOT NULL,
  CONSTRAINT company_pk PRIMARY KEY (tenant_id, id),
  CONSTRAINT company_tenant_tax_id_uk UNIQUE (tenant_id, tax_id),
  CONSTRAINT company_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE INDEX company_tenant_name_idx ON parties.company (tenant_id, name, id);
CREATE INDEX company_tenant_created_at_idx ON parties.company (tenant_id, created_at, id);

ALTER TABLE parties.company ENABLE ROW LEVEL SECURITY;
ALTER TABLE parties.company FORCE ROW LEVEL SECURITY;

CREATE POLICY company_tenant_isolation ON parties.company
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
  WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
