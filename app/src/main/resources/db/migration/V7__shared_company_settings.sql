-- Ajustes de la empresa del tenant (paso 1.9): sus datos como emisor de facturas. Una fila por tenant, creada en el
-- alta con la razón social igual al nombre del tenant. El NIF y la dirección se rellenan después: hacen falta para
-- emitir, no para empezar a usar la aplicación.

CREATE TABLE shared.company_settings
(
  tenant_id               uuid         NOT NULL,
  legal_name              varchar(200) NOT NULL,
  trade_name              varchar(200),
  tax_id                  varchar(15),
  address_line1           varchar(200),
  address_line2           varchar(200),
  postal_code             varchar(20),
  city                    varchar(100),
  province                varchar(100),
  country                 char(2)      NOT NULL DEFAULT 'ES',
  email                   varchar(254),
  phone                   varchar(30),
  website                 varchar(200),
  -- Mes en que empieza el ejercicio fiscal (1 = enero). La numeración de facturas va por ejercicio.
  fiscal_year_start_month smallint     NOT NULL DEFAULT 1,
  time_zone               varchar(64)  NOT NULL DEFAULT 'Europe/Madrid',
  -- Idioma de los documentos y correos que se envían a los clientes.
  language                varchar(5)   NOT NULL DEFAULT 'es',
  logo_path               varchar(512),
  logo_content_type       varchar(50),
  version                 bigint       NOT NULL,
  updated_at              timestamptz  NOT NULL,
  CONSTRAINT company_settings_pk PRIMARY KEY (tenant_id),
  CONSTRAINT company_settings_tenant_fk FOREIGN KEY (tenant_id) REFERENCES shared.tenant (tenant_id),
  CONSTRAINT company_settings_legal_name_not_blank CHECK (length(btrim(legal_name)) > 0),
  CONSTRAINT company_settings_country_iso CHECK (country ~ '^[A-Z]{2}$'),
  CONSTRAINT company_settings_fiscal_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
  CONSTRAINT company_settings_logo CHECK ((logo_path IS NULL) = (logo_content_type IS NULL))
);

-- Historial (paso 1.4): mismas columnas de datos, sin restricciones, con la revisión.
CREATE TABLE shared.company_settings_aud
(
  tenant_id               uuid     NOT NULL,
  rev                     bigint   NOT NULL,
  revtype                 smallint NOT NULL,
  legal_name              varchar(200),
  trade_name              varchar(200),
  tax_id                  varchar(15),
  address_line1           varchar(200),
  address_line2           varchar(200),
  postal_code             varchar(20),
  city                    varchar(100),
  province                varchar(100),
  country                 char(2),
  email                   varchar(254),
  phone                   varchar(30),
  website                 varchar(200),
  fiscal_year_start_month smallint,
  time_zone               varchar(64),
  language                varchar(5),
  logo_path               varchar(512),
  logo_content_type       varchar(50),
  updated_at              timestamptz,
  CONSTRAINT company_settings_aud_pk PRIMARY KEY (tenant_id, rev),
  CONSTRAINT company_settings_aud_revision_fk FOREIGN KEY (tenant_id, rev) REFERENCES shared.revision (tenant_id, id)
);

DO $$
DECLARE
  settings text;
BEGIN
  FOREACH settings IN ARRAY ARRAY['shared.company_settings', 'shared.company_settings_aud']
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', settings);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', settings);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s '
                   'USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) '
                   'WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)', settings);
  END LOOP;
END
$$;
