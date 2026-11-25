-- Empresas completas y contactos (paso 1.10).
--  * pg_trgm busca por trigramas: «turia» encuentra «Distribuciones Turia S.L.» con índice, y similarity() detecta
--    nombres parecidos al dar de alta. btree_gin deja poner tenant_id delante en esos índices, como en todos.
--  * Las dos extensiones son «de confianza» (PostgreSQL 13+): las puede crear el dueño de la base de datos.
--  * La forma de pago de la empresa llega con el paso 1.12, cuando existan las formas de pago.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

ALTER TABLE parties.company
  ADD COLUMN trade_name    varchar(200),
  ADD COLUMN email         varchar(254),
  ADD COLUMN phone         varchar(30),
  ADD COLUMN website       varchar(200),
  ADD COLUMN address_line1 varchar(200),
  ADD COLUMN address_line2 varchar(200),
  ADD COLUMN postal_code   varchar(20),
  ADD COLUMN city          varchar(100),
  ADD COLUMN province      varchar(100),
  ADD COLUMN country       char(2)       NOT NULL DEFAULT 'ES',
  -- En euros. NULL: sin límite.
  ADD COLUMN credit_limit  numeric(19, 4),
  ADD COLUMN is_customer   boolean       NOT NULL DEFAULT true,
  ADD COLUMN is_supplier   boolean       NOT NULL DEFAULT false,
  ADD CONSTRAINT company_country_iso CHECK (country ~ '^[A-Z]{2}$'),
  ADD CONSTRAINT company_credit_limit_positive CHECK (credit_limit IS NULL OR credit_limit >= 0);

ALTER TABLE parties.company_aud
  ADD COLUMN trade_name    varchar(200),
  ADD COLUMN email         varchar(254),
  ADD COLUMN phone         varchar(30),
  ADD COLUMN website       varchar(200),
  ADD COLUMN address_line1 varchar(200),
  ADD COLUMN address_line2 varchar(200),
  ADD COLUMN postal_code   varchar(20),
  ADD COLUMN city          varchar(100),
  ADD COLUMN province      varchar(100),
  ADD COLUMN country       char(2),
  ADD COLUMN credit_limit  numeric(19, 4),
  ADD COLUMN is_customer   boolean,
  ADD COLUMN is_supplier   boolean;

-- Búsqueda libre del listado: lower(name) LIKE '%…%' y tax_id LIKE '%…%' usan estos índices.
CREATE INDEX company_name_trgm_idx ON parties.company USING gin (tenant_id, lower(name) gin_trgm_ops);
CREATE INDEX company_tax_id_trgm_idx ON parties.company USING gin (tenant_id, tax_id gin_trgm_ops);

CREATE TABLE parties.contact
(
  tenant_id  uuid         NOT NULL,
  id         uuid         NOT NULL,
  company_id uuid         NOT NULL,
  first_name varchar(100) NOT NULL,
  last_name  varchar(100),
  job_title  varchar(100),
  email      varchar(254),
  phone      varchar(30),
  -- Para qué se le contacta: facturación, decisor, compras, técnico u otro.
  role       varchar(20),
  version    bigint       NOT NULL,
  created_at timestamptz  NOT NULL,
  updated_at timestamptz  NOT NULL,
  CONSTRAINT contact_pk PRIMARY KEY (tenant_id, id),
  CONSTRAINT contact_company_fk FOREIGN KEY (tenant_id, company_id)
    REFERENCES parties.company (tenant_id, id) ON DELETE CASCADE,
  CONSTRAINT contact_first_name_not_blank CHECK (length(btrim(first_name)) > 0),
  CONSTRAINT contact_role CHECK (role IN ('billing', 'decision_maker', 'purchasing', 'technical', 'other'))
);

CREATE INDEX contact_company_idx ON parties.contact (tenant_id, company_id, first_name, id);

CREATE TABLE parties.contact_aud
(
  tenant_id  uuid     NOT NULL,
  id         uuid     NOT NULL,
  rev        bigint   NOT NULL,
  revtype    smallint NOT NULL,
  company_id uuid,
  first_name varchar(100),
  last_name  varchar(100),
  job_title  varchar(100),
  email      varchar(254),
  phone      varchar(30),
  role       varchar(20),
  created_at timestamptz,
  updated_at timestamptz,
  CONSTRAINT contact_aud_pk PRIMARY KEY (tenant_id, id, rev),
  CONSTRAINT contact_aud_revision_fk FOREIGN KEY (tenant_id, rev) REFERENCES shared.revision (tenant_id, id)
);

DO $$
DECLARE
  contacts text;
BEGIN
  FOREACH contacts IN ARRAY ARRAY['parties.contact', 'parties.contact_aud']
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', contacts);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', contacts);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s '
                   'USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', contacts);
  END LOOP;
END
$$;
